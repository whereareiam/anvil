package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Delivers ordered player events outside the shared RPC reader and contains subscription failures. Events queue
 * without a limit on their number, because the server sends packets in bursts, which a tap of every packet turns into
 * as many events; a player fails when its callbacks make no progress, which bounds what waits by the time a stuck
 * callback is allowed.
 */
final class PlayerEventDispatcher implements AutoCloseable {
	private static final int HISTORY_LIMIT = 80;
	private static final Duration STALL_LIMIT = Duration.ofSeconds(10);
	private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

	private final String player;
	private final Consumer<String> diagnostics;
	private final Duration stallLimit;
	private final ThreadPoolExecutor executor;
	private final AtomicInteger pending = new AtomicInteger();
	/**
	 * When a delivery last finished, or when events began waiting after none did.
	 */
	private volatile long progress = System.nanoTime();
	private final Map<String, CopyOnWriteArrayList<Registration>> listeners = new ConcurrentHashMap<>();
	private final AtomicReference<IllegalStateException> failure = new AtomicReference<>();
	private final Deque<String> history = new ArrayDeque<>();
	private volatile @Nullable Thread dispatchThread;
	private boolean stopping;
	private volatile boolean cancelNormalCallbacks;
	private boolean closed;

	PlayerEventDispatcher(@NotNull String player, @NotNull Consumer<String> diagnostics) {
		this(player, diagnostics, STALL_LIMIT);
	}

	/**
	 * Creates the dispatcher of one player.
	 *
	 * @param stallLimit how long events may wait while no callback finishes before the player fails
	 */
	PlayerEventDispatcher(@NotNull String player, @NotNull Consumer<String> diagnostics, @NotNull Duration stallLimit) {
		this.player = player;
		this.diagnostics = diagnostics;
		this.stallLimit = stallLimit;
		executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
				action -> Thread.ofVirtual().name("anvil-player-events-" + player).unstarted(() -> {
					dispatchThread = Thread.currentThread();
					action.run();
				}));
	}

	synchronized @NotNull Subscription subscribe(@NotNull String event, @NotNull Consumer<JsonNode> listener) {
		if (stopping) throw new IllegalStateException("Player '" + player + "' event channel is closing");
		throwIfFailed();
		Registration registration = new Registration(event, listener);
		listeners.computeIfAbsent(event, ignored -> new CopyOnWriteArrayList<>()).add(registration);
		return registration;
	}

	synchronized void dispatch(@NotNull String event, @NotNull JsonNode payload) {
		if (stopping) return;
		remember(event + " " + payload);
		var subscribed = listeners.get(event);
		if (subscribed == null || subscribed.isEmpty()) return;
		List<Registration> recipients = List.copyOf(subscribed);
		int waiting = pending.get();
		if (waiting > 0 && System.nanoTime() - progress > stallLimit.toNanos()) {
			stopping = true;
			recordFailure("Player '" + player + "' event callbacks finished nothing within " + stallLimit + " while "
					+ waiting + " capability events waited", new IllegalStateException("Event callbacks stalled"));
			executor.shutdownNow();
			return;
		}

		if (pending.getAndIncrement() == 0) progress = System.nanoTime();
		executor.execute(() -> {
			try {
				deliver(event, payload, recipients);
			} finally {
				progress = System.nanoTime();
				pending.decrementAndGet();
			}
		});
	}

	/**
	 * Throws a new exception for the recorded failure, so that every caller, such as a wait and the cleanup after it,
	 * reports it from its own stack and none suppresses the exception another already threw.
	 */
	void throwIfFailed() {
		IllegalStateException eventFailure = failure.get();
		if (eventFailure != null) throw new IllegalStateException(eventFailure.getMessage(), eventFailure.getCause());
	}

	@NotNull String history() {
		synchronized (history) { return history.toString(); }
	}

	/**
	 * Stops normal event admission before the child player is destroyed. Accepted callbacks drain
	 * while their RPCs are still valid; destruction from a callback cancels later normal events.
	 */
	void stop() {
		synchronized (this) {
			stopping = true;
			if (Thread.currentThread() == dispatchThread) {
				cancelNormalCallbacks = true;
				executor.getQueue().clear();
				return;
			}
			executor.shutdown();
		}
		try {
			if (!executor.awaitTermination(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
				executor.shutdownNow();
				clearListeners();
				recordFailure("Player '" + player + "' event callbacks did not finish within " + CLOSE_TIMEOUT,
						new IllegalStateException("Event callback shutdown timed out"));
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			executor.shutdownNow();
			clearListeners();
			recordFailure("Interrupted while closing event callbacks for player '" + player + "'", interrupted);
		}
	}

	private void deliver(String event, JsonNode payload, List<Registration> recipients) {
		for (Registration registration : recipients) {
			if (cancelNormalCallbacks && !event.equals(PlayerConnectionEvent.DESTROYED.getId())) return;
			if (!registration.active.get()) continue;
			try {
				registration.listener.accept(payload);
			} catch (RuntimeException | Error cause) {
				registration.close();
				recordFailure("Capability event '" + event + "' failed for player '" + player + "'", cause);
			}
		}
	}

	private void recordFailure(String message, Throwable cause) {
		IllegalStateException eventFailure = new IllegalStateException(message, cause);
		if (!failure.compareAndSet(null, eventFailure)) return;
		remember(message + ": " + cause.getClass().getSimpleName());
		diagnostics.accept(message + ": " + cause.getClass().getSimpleName());
	}

	private void remember(String event) {
		synchronized (history) {
			history.addLast(event);
			while (history.size() > HISTORY_LIMIT) history.removeFirst();
		}
	}

	@Override
	public void close() {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}
		stop();
		Runnable notifyDestroyed = () -> {
			String event = PlayerConnectionEvent.DESTROYED.getId();
			List<Registration> recipients = List.copyOf(listeners.getOrDefault(event, new CopyOnWriteArrayList<>()));
			try { deliver(event, WorkerMessageCodec.emptyEnvelope(), recipients); }
			finally { clearListeners(); }
		};
		if (Thread.currentThread() == dispatchThread && !executor.isShutdown()) {
			executor.execute(notifyDestroyed);
			executor.shutdown();
			return;
		}
		notifyDestroyed.run();
		throwIfFailed();
	}

	private void clearListeners() {
		listeners.values().forEach(registrations -> registrations.forEach(registration -> registration.active.set(false)));
		listeners.clear();
	}

	@RequiredArgsConstructor
	private final class Registration implements Subscription {
		private final String event;
		private final Consumer<JsonNode> listener;
		private final AtomicBoolean active = new AtomicBoolean(true);

		@Override
		public void close() {
			if (!active.compareAndSet(true, false)) return;
			var subscribed = listeners.get(event);
			if (subscribed != null) subscribed.remove(this);
		}
	}
}
