package me.whereareiam.anvil.player.worker;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A worker player for the tests of worker extensions, which the test drives as a library's worker drives its players:
 * {@link #connect()} opens a new session and runs every connection binding for it, {@link #disconnect()} ends the
 * session and closes them, through the same {@link NativeSessionBindings} every library uses. It speaks no edition
 * protocol, so an extension that needs one refuses it; an edition's kit offers a player that speaks its protocol.
 *
 * <p>The player keeps what extensions emit, by event, and the view rotation they set. It starts disconnected, as a
 * worker's player does when its extensions bind.</p>
 *
 * <pre>{@code
 * TestWorkerPlayer player = new TestWorkerPlayer("Alice");
 * WorkerBinding binding = extension.bind(player, new TestOperations());
 * player.connect();
 * List<HeldCookies> held = player.emitted(CookiesChannel.HELD);
 * }</pre>
 */
public class TestWorkerPlayer implements PlayerBindingContext<Object> {
	/**
	 * The library the test players belong to, which their protocols name when they refuse something.
	 */
	public static final String LIBRARY_ID = "test";
	/**
	 * The client version of a test player unless its test names another: Minecraft 1.21.11.
	 */
	public static final MinecraftVersion CLIENT_VERSION = MinecraftVersion.parse("1.21.11");

	private final String name;
	private final UUID uniqueId;
	private final MinecraftVersion clientVersion;
	private final NativeSessionBindings sessions = new NativeSessionBindings();
	private final WorkerProtocols noProtocols = WorkerProtocols.builder(LIBRARY_ID).build();
	private final List<Emitted> emitted = new CopyOnWriteArrayList<>();
	private volatile @Nullable Object session;
	private volatile @NotNull ViewRotation rotation = new ViewRotation(0, 0);

	/**
	 * Creates a disconnected player of the {@link #CLIENT_VERSION default client version} whose unique ID derives from
	 * its name, as an offline player's does.
	 *
	 * @param name the player's name
	 */
	public TestWorkerPlayer(@NotNull String name) {
		this(name, CLIENT_VERSION);
	}

	/**
	 * Creates a disconnected player of a client version whose unique ID derives from its name, as an offline player's
	 * does.
	 *
	 * @param name the player's name
	 * @param clientVersion the Minecraft version of the player's client
	 */
	public TestWorkerPlayer(@NotNull String name, @NotNull MinecraftVersion clientVersion) {
		this.name = name;
		this.uniqueId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
		this.clientVersion = clientVersion;
	}

	@Override
	public @NotNull String name() {
		return name;
	}

	@Override
	public @NotNull UUID uniqueId() {
		return uniqueId;
	}

	@Override
	public @NotNull MinecraftVersion clientVersion() {
		return clientVersion;
	}

	/**
	 * Opens a new session, unless one is open, and runs every connection binding for it.
	 */
	@Override
	public synchronized void connect() {
		if (session != null) return;

		Object opened = new Object();
		session = opened;
		sessions.attach(opened);
	}

	/**
	 * Ends the open session, if any, and closes the bindings of its connection.
	 */
	@Override
	public synchronized void disconnect() {
		Object current = session;
		if (current == null) return;

		session = null;
		sessions.end(current);
		disconnected();
	}

	@Override
	public synchronized void rejoin() {
		disconnect();
		connect();
	}

	/**
	 * Tells whether a session is open.
	 *
	 * @return whether the player is connected
	 */
	public boolean isConnected() {
		return session != null;
	}

	@Override
	public @NotNull Object nativeSession() {
		Object current = session;
		if (current == null) throw new IllegalStateException("Player '" + name + "' is not connected");

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
		return rotation;
	}

	@Override
	public void viewRotation(@NotNull ViewRotation rotation) {
		this.rotation = rotation;
	}

	@Override
	public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
		emitted.add(new Emitted(event.getId(), payload));
	}

	/**
	 * Returns the payloads extensions emitted for one event, in the order they emitted them.
	 *
	 * @param event the event
	 * @param <E> payload type
	 * @return the payloads, {@code null} for an event emitted without one
	 */
	public <E> @NotNull List<E> emitted(@NotNull EventDescriptor<E> event) {
		return emitted.stream()
				.filter(emitted -> emitted.id().equals(event.getId()))
				.map(emitted -> event.getPayloadType().cast(emitted.payload()))
				.toList();
	}

	/**
	 * Returns the IDs of every event extensions emitted, in the order they emitted them.
	 *
	 * @return the event IDs
	 */
	public @NotNull List<String> emittedEvents() {
		return emitted.stream().map(Emitted::id).toList();
	}

	@Override
	public <P> @NotNull P protocol(@NotNull Class<P> type) {
		return protocols().protocol(type);
	}

	/**
	 * Returns the protocols the player speaks; none, unless an edition's player speaks its own.
	 *
	 * @return the spoken protocols
	 */
	protected @NotNull WorkerProtocols protocols() {
		return noProtocols;
	}

	/**
	 * Runs once the player's session ended, after the bindings of its connection closed, as a library tells its
	 * protocol that the connection closed.
	 */
	protected void disconnected() {
	}

	private record Emitted(String id, @Nullable Object payload) {
	}
}
