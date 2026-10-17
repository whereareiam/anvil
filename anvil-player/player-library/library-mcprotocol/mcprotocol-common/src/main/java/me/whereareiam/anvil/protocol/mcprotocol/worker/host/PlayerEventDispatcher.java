package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerPlayerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Delivers bounded, ordered player events outside the shared RPC reader and contains subscription failures.
 */
final class PlayerEventDispatcher implements AutoCloseable {
	private static final int HISTORY_LIMIT = 80;
	private static final int PENDING_LIMIT = 256;
	private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

	private final String player;
	private final Consumer<String> diagnostics;
	private final ThreadPoolExecutor executor;
	private final Map<String, CopyOnWriteArrayList<Registration>> listeners = new ConcurrentHashMap<>();
	private final AtomicReference<IllegalStateException> failure = new AtomicReference<>();
	private final Deque<String> history = new ArrayDeque<>();
	private volatile @Nullable Thread dispatchThread;
	private boolean stopping;
	private volatile boolean cancelNormalCallbacks;
	private boolean closed;

	PlayerEventDispatcher(@NotNull String player, @NotNull Consumer<String> diagnostics) {
		this.player = player;
		this.diagnostics = diagnostics;
		executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(PENDING_LIMIT),
				action -> Thread.ofVirtual().name("anvil-player-events-" + player).unstarted(() -> {
					dispatchThread = Thread.currentThread();
					action.run();
				}));
	}

	synchronized @NotNull ProtocolSubscription subscribe(@NotNull String event, @NotNull Consumer<JsonNode> listener) {
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
		try {
			executor.execute(() -> deliver(event, payload, recipients));
		} catch (RejectedExecutionException overflow) {
			stopping = true;
			recordFailure("Player '" + player + "' exceeded " + PENDING_LIMIT + " pending capability events", overflow);
			executor.shutdownNow();
		}
	}

	void throwIfFailed() {
		IllegalStateException eventFailure = failure.get();
		if (eventFailure != null) throw eventFailure;
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
			if (cancelNormalCallbacks && !event.equals(WorkerPlayerEvent.DESTROYED.getWireName())) return;
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
			String event = WorkerPlayerEvent.DESTROYED.getWireName();
			try { deliver(event, WorkerMessageCodec.message(new byte[0]), List.copyOf(listeners.getOrDefault(event, new CopyOnWriteArrayList<>()))); }
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
	private final class Registration implements ProtocolSubscription {
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
