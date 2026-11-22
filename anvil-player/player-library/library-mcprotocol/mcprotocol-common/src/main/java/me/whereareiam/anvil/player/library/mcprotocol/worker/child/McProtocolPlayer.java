package me.whereareiam.anvil.player.library.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.Accessors;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.worker.JavaConnection;
import me.whereareiam.anvil.edition.java.protocol.worker.JavaPacketDispatcher;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.player.library.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.player.library.mcprotocol.model.worker.WorkerPlayerCapabilities;
import me.whereareiam.anvil.player.library.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.player.library.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.player.library.mcprotocol.worker.transport.WorkerMessageWriter;
import me.whereareiam.anvil.player.worker.NativeSessionBindings;
import me.whereareiam.anvil.player.worker.WorkerExtensions;
import me.whereareiam.anvil.player.worker.WorkerOperation;
import me.whereareiam.anvil.player.worker.WorkerProtocols;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * One worker player, which binds the worker's extensions as their {@code PlayerBindingContext}. Native sessions come
 * from the release's client segment and stay opaque here; this player owns their generations, the connection
 * events, and its Java Edition protocol, a {@link JavaPacketDispatcher} over the release's client, follows the current
 * session; the player's view is that protocol's client's own player's. Adapters come from the worker's verified
 * segments.
 *
 * <p>Whether the current session counts as connected is its {@link SessionReports}' to say, not the channel's: a
 * connection's close, whether its channel reports it, the player disconnects or opens a session in its place, reaches
 * the protocol first, so the client never ticks into a session that already counts as gone.</p>
 */
@Accessors(fluent = true)
final class McProtocolPlayer implements PlayerBindingContext<Object>, AutoCloseable {
	private final String id;
	private final @NotNull WorkerPlayerOptions options;
	private final @NotNull MinecraftVersion clientVersion;
	private final McProtocolClient<Object> client;
	private final WorkerSegments segments;
	private final WorkerMessageWriter events;
	private final WorkerExtensions capabilities;
	private final JavaPacketDispatcher protocol;
	private final WorkerProtocols protocols;

	private final @NotNull WorkerMessageCodec codec = new WorkerMessageCodec();
	private final NativeSessionBindings nativeBindings = new NativeSessionBindings();
	private final SessionReports reports;

	private WorkerExtensions.PlayerBindings bindings;
	private volatile @Nullable Object session;

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
		this.clientVersion = MinecraftVersion.parse(options.getMinecraftVersion());
		this.client = client;
		this.segments = segments;
		this.events = events;
		this.capabilities = capabilities;
		this.protocol = new JavaPacketDispatcher(McProtocolClient.LIBRARY_ID, client.protocolNumber(), clientVersion,
				options.getProfile().getName(), new SessionConnection());
		this.reports = new SessionReports(this, protocol, nativeBindings);
		this.protocols = WorkerProtocols.builder(McProtocolClient.LIBRARY_ID)
				.speak(JavaProtocol.class, protocol, protocol::scoped)
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
			protocol.start();
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
	public @NotNull MinecraftVersion clientVersion() {
		return clientVersion;
	}

	@Override
	public @NotNull String name() {
		return options.getProfile().getName();
	}

	@Override
	public synchronized void connect() {
		Object current = session;
		if (current != null && client.connected(current)) return;

		open();
	}

	@Override
	public synchronized void disconnect() {
		Object current = session;
		if (current == null || !client.connected(current)) return;

		reports.disconnecting(current);
		client.disconnect(current, "Disconnected by Anvil");
	}

	@Override
	public synchronized void rejoin() {
		disconnect();
		open();
	}

	/**
	 * Returns the current session while it counts as connected: from the moment its channel reported the connection
	 * open until the player's protocol heard of its close, which may come a little after the channel closed.
	 */
	@Override
	public @NotNull Object nativeSession() {
		Object current = session;
		if (current == null || !reports.connected())
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
		return protocol.player().rotation();
	}

	@Override
	public void viewRotation(@NotNull ViewRotation rotation) {
		protocol.player().rotation(rotation);
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
	public void close() {
		try (protocol; nativeBindings; var ignored = bindings) {
			disconnect();
		} finally {
			session = null;
		}
	}

	/**
	 * Opens a new session and connects it. A session it replaces closes for the player's protocol first, and its later
	 * reports are ignored.
	 */
	private void open() {
		Object replaced = session;
		if (replaced != null) reports.replacing(replaced);
		session = null;

		reports.connecting();
		Object created = client.open(PlayerLogin.of(options), reports);
		session = created;
		nativeBindings.attach(created);
		client.connect(created);
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

		@Override
		public boolean handsOverFrames() {
			return true;
		}
	}
}
