package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Core player state shared with worker capabilities through a narrow provider API. Native sessions come from
 * the release's client segment and stay opaque here; this player owns their generations, the connection
 * events and the view rotation. Adapters come from the worker's verified segments.
 */
@RequiredArgsConstructor
@Accessors(fluent = true)
final class McProtocolPlayer implements NativePlayer<Object>, AutoCloseable {
	private static final String CLIENT_LOCALE = "en_us";
	private static final int VIEW_DISTANCE = 8;
	private static final String CONNECTION_EVENT = "player.connection";

	private final String id;
	@Getter
	private final @NotNull String name;
	private final String host;
	private final int port;
	@Getter
	private final @NotNull UUID uuid;
	private final @Nullable String accessToken;
	private final McProtocolClient<Object> client;
	private final WorkerSegments segments;
	private final WorkerMessageWriter events;
	private final WorkerCapabilityRegistry capabilities;

	private final @NotNull WorkerMessageCodec codec = new WorkerMessageCodec();
	private final AtomicBoolean disconnectNotified = new AtomicBoolean();
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
		return uuid;
	}

	@Override
	public synchronized void connect() {
		Object current = session;
		if (current != null && client.connected(current)) return;

		disconnectNotified.set(false);
		ClientLogin login = ClientLogin.builder()
				.name(name)
				.uniqueId(uuid)
				.host(host)
				.port(port)
				.accessToken(accessToken)
				.locale(CLIENT_LOCALE)
				.viewDistance(VIEW_DISTANCE)
				.build();
		Object created = client.open(login, new Listener());
		session = created;
		nativeBindings.attach(created);
		client.connect(created);
	}

	@Override
	public synchronized void disconnect() {
		Object current = session;
		if (current != null && client.connected(current))
			client.disconnect(current, "Disconnected by Anvil");
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
			throw new IllegalStateException("Player '" + name + "' is not connected");
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
	private final class Listener implements ClientListener<Object> {
		@Override
		public void loggedIn(@NotNull Object current) {
			if (current != session) return;
			emit(CONNECTION_EVENT, codec.connection(true, null));
		}

		@Override
		public void teleported(@NotNull Object current, float yaw, float pitch) {
			if (current != session) return;
			view(yaw, pitch);
		}

		@Override
		public void disconnected(@NotNull Object current, @NotNull String reason) {
			if (current != session) return;
			if (!disconnectNotified.compareAndSet(false, true)) return;
			emit(CONNECTION_EVENT, codec.connection(false, reason));
		}
	}
}
