package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.Accessors;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.worker.JavaConnection;
import me.whereareiam.anvil.protocol.java.worker.JavaPacketDispatcher;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerProfile;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import me.whereareiam.anvil.protocol.worker.NativeSessionBindings;
import me.whereareiam.anvil.protocol.worker.WorkerExtensions;
import me.whereareiam.anvil.protocol.worker.WorkerOperation;
import me.whereareiam.anvil.protocol.worker.WorkerProtocols;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * One worker player, which binds the worker's extensions as their {@code PlayerBindingContext}. Native sessions come
 * from the release's client segment and stay opaque here; this player owns their generations, the connection
 * events and the view rotation, and its Java Edition protocol, a {@link JavaPacketDispatcher} over the release's
 * client, follows the current session, as does the world the dispatcher keeps, which forgets its chunks once the
 * session closes. Adapters come from the worker's verified segments.
 */
@Accessors(fluent = true)
final class McProtocolPlayer implements PlayerBindingContext<Object>, AutoCloseable {
	private static final String CLIENT_LOCALE = "en_us";
	private static final int VIEW_DISTANCE = 8;
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
	private final McProtocolClient<Object> client;
	private final WorkerSegments segments;
	private final WorkerMessageWriter events;
	private final WorkerExtensions capabilities;
	private final JavaPacketDispatcher protocol;
	private final WorkerProtocols protocols;

	private final @NotNull WorkerMessageCodec codec = new WorkerMessageCodec();
	private final AtomicBoolean disconnectNotified = new AtomicBoolean();
	private volatile boolean disconnecting;
	private final NativeSessionBindings nativeBindings = new NativeSessionBindings();

	private WorkerExtensions.PlayerBindings bindings;
	private volatile @Nullable Object session;
	private volatile @NotNull ViewRotation rotation = new ViewRotation(0, 0);

	McProtocolPlayer(
			String id,
			@NotNull WorkerPlayerOptions options,
			McProtocolClient<Object> client,
			WorkerSegments segments,
			WorkerMessageWriter events,
			WorkerExtensions capabilities
	) {
		this.id = id;
		this.options = options;
		this.client = client;
		this.segments = segments;
		this.events = events;
		this.capabilities = capabilities;
		this.protocol = new JavaPacketDispatcher(McProtocolClient.LIBRARY_ID, new SessionConnection());
		this.protocols = WorkerProtocols.builder(McProtocolClient.LIBRARY_ID)
				.speak(JavaProtocol.class, protocol, protocol::scoped)
				.speak(JavaWorld.class, protocol.world(), track -> protocol.world())
				.build();
	}

	/**
	 * Binds the worker's capabilities to this player.
	 *
	 * @return capabilities bound for this player and those unavailable to it
	 */
	@NotNull WorkerPlayerCapabilities initialize() {
		try {
			bindings = capabilities.bind(this, protocols);
			return WorkerPlayerCapabilities.builder()
					.capabilities(capabilities.capabilities())
					.unavailable(capabilities.unavailable())
					.build();
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
		return execute(bindings.operation(operation), arguments);
	}

	private <Q, R> JsonNode execute(WorkerOperation<Q, R> operation, JsonNode arguments) {
		return codec.handler(operation.getDescriptor(), operation::apply).apply(arguments);
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
		Object created = client.open(login(), new Listener());
		session = created;
		nativeBindings.attach(created);
		client.connect(created);
	}

	/**
	 * Builds the client login from the resolved options, with the worker's own client settings.
	 */
	private ClientLogin login() {
		WorkerProfile profile = options.getProfile();
		WorkerCredentials credentials = options.getCredentials();
		WorkerConnection connection = options.getConnection();
		return ClientLogin.builder()
				.profile(ClientProfile.builder().name(profile.getName()).uniqueId(profile.getUniqueId()).build())
				.credentials(credentials == null ? null : ClientCredentials.builder()
						.accessToken(credentials.getAccessToken())
						.sessionServer(credentials.getSessionServer())
						.build())
				.settings(ClientSettings.builder().locale(CLIENT_LOCALE).viewDistance(VIEW_DISTANCE).build())
				.connection(ClientConnection.builder()
						.host(connection.getHost())
						.port(connection.getPort())
						.virtualHost(connection.getVirtualHost())
						.sourceAddress(connection.getSourceAddress())
						.build())
				.build();
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
	public synchronized @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
		return nativeBindings.register(listener);
	}

	@Override
	public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
		events.event(id, event.getId(), codec.envelope(event.getPayloadType().cast(payload)));
	}

	@Override
	public @NotNull ViewRotation viewRotation() {
		return rotation;
	}

	@Override
	public void viewRotation(@NotNull ViewRotation rotation) {
		this.rotation = rotation;
	}

	@Override
	public <P> @NotNull P protocol(@NotNull Class<P> type) {
		return protocols.protocol(type);
	}

	@Override
	public <P> @NotNull P adapter(@NotNull Class<P> port) {
		return segments.adapter(port);
	}

	@Override
	public @NotNull MinecraftVersion minecraftVersion() {
		return MinecraftVersion.parse(options.getClientVersion());
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
	 * Sends the player's packets on its current session through the release's client segment.
	 */
	private final class SessionConnection implements JavaConnection {
		@Override
		public void send(@NotNull ServerboundPacket packet) {
			client.send(nativeSession(), packet);
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return client.supports(type);
		}
	}

	/**
	 * Turns the client's callbacks for the current session generation into player events; callbacks of a
	 * replaced session are ignored.
	 */
	private final class Listener implements ClientListener<Object> {
		@Override
		public void granted(@NotNull Object current, @NotNull ClientProfile granted) {
			if (current != session) return;
			emit(PlayerConnectionEvent.GRANTED, new PlayerProfile(granted.getName(), granted.getUniqueId()));
		}

		@Override
		public void loggedIn(@NotNull Object current, @NotNull ClientProfile granted) {
			if (current != session) return;
			PlayerProfile profile = new PlayerProfile(granted.getName(), granted.getUniqueId());
			emit(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(true, null, null, profile));
		}

		@Override
		public void teleported(@NotNull Object current, float yaw, float pitch) {
			if (current != session) return;
			viewRotation(new ViewRotation(yaw, pitch));
		}

		@Override
		public void disconnected(@NotNull Object current, @NotNull DisconnectCause cause, @NotNull String reason) {
			if (current != session) return;

			protocol.disconnected();
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

		@Override
		public void received(@NotNull Object current, @NotNull ClientboundPacket packet) {
			if (current != session) return;
			protocol.deliver(packet);
		}

		private void report(Object current, DisconnectCause cause, String reason) {
			if (current != session) return;
			if (!disconnectNotified.compareAndSet(false, true)) return;

			try {
				nativeBindings.end(current);
			} finally {
				emit(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(false, reason, cause, null));
			}
		}
	}
}
