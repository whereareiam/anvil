package external.fixture.java;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.worker.JavaConnection;
import me.whereareiam.anvil.protocol.java.worker.JavaPacketDispatcher;
import me.whereareiam.anvil.protocol.worker.NativeSessionBindings;
import me.whereareiam.anvil.protocol.worker.WorkerExtensions;
import me.whereareiam.anvil.protocol.worker.WorkerOperation;
import me.whereareiam.anvil.protocol.worker.WorkerProtocols;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * One player of the in-process library. It is both the host's {@link ProtocolPlayer} and the worker extensions'
 * {@link PlayerBindingContext}: its channel calls the extensions' operations directly and hands their events to the
 * host's listeners, without a transport between them.
 */
final class JavaFixturePlayer implements ProtocolPlayer, PlayerBindingContext<Object> {
	private final PlayerRequest request;
	private final ProtocolRelease release;
	private final PlayerProfile profile;
	private final NativeSessionBindings sessions = new NativeSessionBindings();
	private final JavaPacketDispatcher protocol;
	private final EchoServer server = new EchoServer();
	private final WorkerProtocols protocols;
	private final InProcessChannel channel;
	private final WorkerExtensions.PlayerBindings bindings;

	private volatile @Nullable Object session;
	private volatile @Nullable PlayerProfile granted;
	private volatile boolean destroyed;

	JavaFixturePlayer(PlayerRequest request, ProtocolRelease release, WorkerExtensions extensions) {
		this.request = request;
		this.release = release;
		this.channel = new InProcessChannel(this, extensions);
		this.profile = new PlayerProfile(request.getUsername(),
				UUID.nameUUIDFromBytes(request.getUsername().getBytes(StandardCharsets.UTF_8)));
		this.protocol = new JavaPacketDispatcher(JavaFixtureLibraryProvider.ID, release.getProtocolNumber(),
				request.getClientVersion(), request.getName(), new EchoConnection());
		this.protocols = WorkerProtocols.builder(JavaFixtureLibraryProvider.ID)
				.speak(JavaProtocol.class, protocol, protocol::scoped)
				.build();
		this.bindings = extensions.bind(this, protocols);
		protocol.start();
	}

	@Override
	public @NotNull String name() {
		return request.getName();
	}

	@Override
	public @NotNull UUID uniqueId() {
		return profile.getUniqueId();
	}

	@Override
	public @NotNull MinecraftVersion clientVersion() {
		return request.getClientVersion();
	}

	@Override
	public @NotNull String libraryId() {
		return JavaFixtureLibraryProvider.ID;
	}

	@Override
	public @NotNull ProtocolRelease release() {
		return release;
	}

	@Override
	public @NotNull PlayerProfile requestedProfile() {
		return profile;
	}

	@Override
	public @Nullable PlayerProfile grantedProfile() {
		return granted;
	}

	@Override
	public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
		return Optional.empty();
	}

	@Override
	public @NotNull Optional<ProtocolChannel> channel() {
		return Optional.of(channel);
	}

	@Override
	public synchronized void connect() {
		if (session != null) return;

		Object opened = new Object();
		session = opened;
		sessions.attach(opened);
		granted = profile;
		emit(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(true, null, null, profile));
		server.join().forEach(protocol::deliver);
	}

	@Override
	public synchronized void disconnect() {
		Object current = session;
		if (current == null) return;

		session = null;
		granted = null;
		sessions.end(current);
		protocol.disconnected();
		var ended = new PlayerConnectionEvent(false, "Disconnected by Anvil", DisconnectCause.CLIENT, null);
		emit(PlayerConnectionEvent.CHANGED, ended);
	}

	@Override
	public synchronized void rejoin() {
		disconnect();
		connect();
	}

	@Override
	public @NotNull Object nativeSession() {
		Object current = session;
		if (current == null) throw new IllegalStateException("Player '" + name() + "' is not connected");

		return current;
	}

	@Override
	public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
		return session == nativeSession;
	}

	@Override
	public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
		return sessions.register(listener);
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
	public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
		channel.publish(event, payload);
	}

	@Override
	public <P> @NotNull P protocol(@NotNull Class<P> type) {
		return protocols.protocol(type);
	}

	@Override
	public <P> @NotNull P adapter(@NotNull Class<P> port) {
		throw new ProtocolUnavailableException("the " + JavaFixtureLibraryProvider.ID + " library has no segments");
	}

	@Override
	public boolean destroyed() {
		return destroyed;
	}

	@Override
	public synchronized void destroy() {
		if (destroyed) return;

		disconnect();
		destroyed = true;
		emit(PlayerConnectionEvent.DESTROYED, null);
		try (protocol; sessions; bindings) {
			channel.close();
		}
	}

	/**
	 * Returns an operation the player's extensions registered, for the host's side of its channel.
	 */
	@NotNull WorkerOperation<?, ?> operation(@NotNull String id) {
		return bindings.operation(id);
	}

	/**
	 * Sends the player's packets to the echo server on its current session and delivers the server's answers.
	 */
	private final class EchoConnection implements JavaConnection {
		@Override
		public void send(@NotNull ServerboundPacket packet) {
			if (!server.carries(packet.getClass()))
				throw new ProtocolUnavailableException("the loaded " + JavaFixtureLibraryProvider.ID
						+ " release cannot send " + packet.getClass().getName());

			nativeSession();
			server.answer(name(), packet).forEach(protocol::deliver);
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return server.carries(type);
		}

		@Override
		public boolean handsOverFrames() {
			return false;
		}
	}
}
