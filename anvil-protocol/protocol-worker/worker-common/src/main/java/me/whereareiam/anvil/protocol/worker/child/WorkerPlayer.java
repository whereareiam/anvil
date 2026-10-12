package me.whereareiam.anvil.protocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import me.whereareiam.anvil.protocol.api.worker.WorkerClient;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageWriter;
import me.whereareiam.anvil.protocol.worker.type.WorkerPlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Core player state shared with worker capabilities through a narrow provider API. Native sessions come from
 * the library's {@link WorkerClient} and stay opaque here; this player owns their generations, the connection
 * events and the view rotation. Adapters come from the worker's verified segments.
 */
@RequiredArgsConstructor
@Accessors(fluent = true)
final class WorkerPlayer implements NativePlayer<Object>, AutoCloseable {
	/**
	 * How long a closed connection waits for the reason of its closing. Client libraries handle packets on
	 * another thread than connection events, so the close can be reported before the server's disconnect packet.
	 */
	private static final long CLOSE_REASON_GRACE_MILLIS = 300;
	private static final ScheduledExecutorService CLOSE_REPORTS = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "anvil-close-reports");
		thread.setDaemon(true);
		return thread;
	});

	private final String id;
	private final @NotNull WorkerPlayerOptions options;
	private final WorkerClient client;
	private final WorkerSegments segments;
	private final WorkerMessageWriter events;
	private final WorkerCapabilityRegistry capabilities;

	private final @NotNull WorkerMessageCodec codec = new WorkerMessageCodec();
	private final AtomicBoolean disconnectNotified = new AtomicBoolean();
	private volatile boolean disconnecting;
	private final NativeSessionBindings nativeBindings = new NativeSessionBindings();

	private WorkerCapabilityRegistry.PlayerBindings bindings;
	private volatile @Nullable Object session;
	private volatile float yaw;
	private volatile float pitch;

	/**
	 * Binds the worker's capabilities to this player.
	 *
	 * @return capabilities bound for this player and those unavailable to it
	 */
	@NotNull WorkerPlayerCapabilities initialize() {
		try {
			bindings = capabilities.bind(this);
			return capabilities.report();
		} catch (RuntimeException | Error failure) {
			try {
				close();
			} catch (RuntimeException | Error cleanup) {
				if (failure != cleanup) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	@NotNull JsonNode execute(@NotNull String operation, @NotNull JsonNode arguments) {
		return bindings.execute(operation, arguments);
	}

	@Override
	public @NotNull UUID uniqueId() {
		return options.getProfile().getUniqueId();
	}

	@Override
	public @NotNull String name() {
		return options.getProfile().getName();
	}

	@Override
	public synchronized void connect() {
		Object current = session;
		if (current != null && client.connected(current)) return;

		disconnectNotified.set(false);
		disconnecting = false;
		Object created = client.open(options, new Listener());
		session = created;
		nativeBindings.attach(created);
		client.connect(created);
	}

	@Override
	public synchronized void disconnect() {
		Object current = session;
		if (current != null && client.connected(current)) {
			disconnecting = true;
			client.disconnect(current, "Disconnected by Anvil");
		}
	}

	@Override
	public synchronized void rejoin() {
		disconnect();
		session = null;
		connect();
	}

	@Override
	public @NotNull Object nativeSession() {
		Object current = session;
		if (current == null || !client.connected(current))
			throw new IllegalStateException("Player '" + options.getProfile().getName() + "' is not connected");
		return current;
	}

	@Override
	public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
		return session == nativeSession;
	}

	@Override
	public synchronized @NotNull ProtocolSubscription bindNativeSession(@NotNull Function<Object, ProtocolSubscription> listener) {
		return nativeBindings.register(listener);
	}

	@Override
	public void emit(@NotNull String event, byte @NotNull [] payload) {
		events.event(id, event, WorkerMessageCodec.message(payload));
	}

	@Override
	public float yaw() {
		return yaw;
	}

	@Override
	public float pitch() {
		return pitch;
	}

	@Override
	public void view(float yaw, float pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
	}

	@Override
	public <P> @NotNull P adapter(@NotNull Class<P> port) {
		return segments.adapter(port);
	}

	@Override
	public void close() {
		try (nativeBindings; var ignored = bindings) {
			disconnect();
		} finally {
			session = null;
		}
	}

	/**
	 * Turns the client's callbacks for the current session generation into player events; callbacks of a
	 * replaced session are ignored.
	 */
	private final class Listener implements WorkerClient.SessionListener {
		@Override
		public void loggedIn(@NotNull Object current, @NotNull PlayerProfile granted) {
			if (current != session) return;
			emit(WorkerPlayerEvent.CONNECTION.getWireName(), codec.connected(granted));
		}

		@Override
		public void teleported(@NotNull Object current, float yaw, float pitch) {
			if (current != session) return;
			view(yaw, pitch);
		}

		@Override
		public void disconnected(@NotNull Object current, @NotNull DisconnectCause cause, @NotNull String reason) {
			if (current != session) return;
			if (disconnecting) {
				report(current, DisconnectCause.CLIENT, reason);
				return;
			}
			if (cause != DisconnectCause.CONNECTION_LOST) {
				report(current, cause, reason);
				return;
			}

			// The reason of this close may still be on its way; it wins when it arrives within the grace period.
			CLOSE_REPORTS.schedule(() -> report(current, cause, reason), CLOSE_REASON_GRACE_MILLIS, TimeUnit.MILLISECONDS);
		}

		private void report(Object current, DisconnectCause cause, String reason) {
			if (current != session) return;
			if (!disconnectNotified.compareAndSet(false, true)) return;
			emit(WorkerPlayerEvent.CONNECTION.getWireName(), codec.disconnected(cause, reason));
		}
	}
}
