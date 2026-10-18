package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Native lifecycle controls, session access, rotation, and event delivery supplied to one player's binding.
 * This context remains associated with that player until destruction. Native sessions returned by
 * {@link #nativeSession()} and supplied to {@link #bindNativeSession(Function)} represent individual
 * connection generations; callers must not retain them across reconnects. Bindings that speak the player's
 * {@link #protocol(Class) protocol} never touch native sessions: they follow connections through
 * {@link #bindConnection(Supplier)}, and only external segmented code reaches the library's sessions.
 *
 * @param <S> native session type of the protocol library
 */
public interface PlayerBindingContext<S> {
	/**
	 * Returns the authenticated player name.
	 *
	 * @return player name
	 */
	@NotNull String name();

	/**
	 * Returns the client profile identity.
	 *
	 * @return client UUID
	 */
	@NotNull UUID uniqueId();

	/**
	 * Starts the native login sequence.
	 */
	void connect();

	/**
	 * Disconnects the current native session.
	 */
	void disconnect();

	/**
	 * Replaces the native session and starts login again.
	 */
	void rejoin();

	/**
	 * Returns the current connected native session.
	 *
	 * @return native session
	 * @throws IllegalStateException when the player is not connected
	 */
	@NotNull S nativeSession();

	/**
	 * Tests whether a callback still belongs to the current native connection generation.
	 * Listeners use this check to ignore callbacks already in flight during a reconnect.
	 *
	 * @param nativeSession native session captured when the listener was installed
	 * @return whether the session is still current
	 */
	boolean isCurrentNativeSession(@NotNull S nativeSession);

	/**
	 * Binds native listeners before each session starts connecting. A session's binding is closed when its
	 * connection ends, before the player reports the disconnect, and at the latest before a replacement is
	 * installed; closing the returned subscription also closes the current binding. The callback must not start the
	 * native session itself.
	 *
	 * @param listener factory returning cleanup for the supplied native generation
	 * @return owned generation-listener registration
	 */
	@NotNull Subscription bindNativeSession(@NotNull Function<S, Subscription> listener);

	/**
	 * Follows the player's connections without touching the library's sessions: the listener runs before each
	 * connection of the player starts logging in, and the subscription it returns closes when that connection ends,
	 * before the player reports the disconnect, and at the latest before a new connection replaces it. Closing the
	 * returned subscription also closes the current connection's.
	 *
	 * <pre>{@code
	 * Subscription connections = player.bindConnection(() -> {
	 *     player.emit(ExampleChannel.RESET, null);
	 *     return state::clear;
	 * });
	 * }</pre>
	 *
	 * @param listener starts the state of a new connection and returns what ends it
	 * @return registration that stops following connections when closed
	 */
	default @NotNull Subscription bindConnection(@NotNull Supplier<Subscription> listener) {
		return bindNativeSession(ignored -> listener.get());
	}

	/**
	 * Returns the latest shared yaw and pitch, including server corrections.
	 *
	 * @return immutable view rotation in degrees
	 */
	@NotNull ViewRotation viewRotation();

	/**
	 * Records the view rotation used by native operations. This updates shared state without
	 * sending a packet; the native implementation remains responsible for transmitting its action.
	 *
	 * @param rotation new yaw and pitch in degrees
	 */
	void viewRotation(@NotNull ViewRotation rotation);

	/**
	 * Emits a typed player event to the host's subscribers of this player's channel.
	 *
	 * @param event registered event schema
	 * @param payload event value, or {@code null} for a {@link Void} event
	 * @param <E> event payload type
	 */
	<E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload);

	/**
	 * Returns the protocol this player speaks, such as the Java Edition protocol, for the binding to listen to the
	 * packets the player receives and to send its own. The protocol belongs to the player, not to one connection: a
	 * listener receives the packets of whichever connection is current, and sending uses the current connection.
	 *
	 * <pre>{@code
	 * public WorkerBinding bind(PlayerBindingContext<Object> player, OperationRegistry operations) {
	 *     JavaProtocol protocol = player.protocol(JavaProtocol.class);
	 *     Subscription chat = protocol.listen(ClientboundSystemChat.class, packet -> ...);
	 *     return chat::close;
	 * }
	 * }</pre>
	 *
	 * <p>A context outside a library worker, such as a test double, speaks no protocol; this default reports that.</p>
	 *
	 * @param type protocol interface, such as {@code JavaProtocol}
	 * @param <P> protocol type
	 * @return the player's protocol of that type
	 * @throws ProtocolUnavailableException when the player does not speak that protocol; the worker reports the
	 * binding's capability as unavailable with the exception's message
	 */
	default <P> @NotNull P protocol(@NotNull Class<P> type) {
		throw new ProtocolUnavailableException("This player binding context speaks no " + type.getName());
	}

	/**
	 * Returns the adapter of a library-neutral port for this player's library release: the implementation that the
	 * segment selected for the release provides. The wiring of a capability with segments of its own, such as an
	 * external capability that sends packets the player's protocol does not model, obtains every release-specific
	 * implementation this way, never through a service lookup of its own, so the worker's segment selection and
	 * linkage self-check decide what it receives. Call it while binding, not in the extension's constructor.
	 *
	 * <pre>{@code
	 * public WorkerBinding bind(PlayerBindingContext<Object> player, OperationRegistry operations) {
	 *     TeleportPackets<?> packets = player.adapter(TeleportPackets.class);
	 *     return new TeleportBinding<>(packets).bind(player, operations);
	 * }
	 * }</pre>
	 *
	 * <p>A context outside a library worker, such as a test double, provides no adapters; this default reports
	 * that.</p>
	 *
	 * @param port port interface the adapter implements
	 * @param <P> port type
	 * @return the adapter, which is stateless and may serve other players of the same worker
	 * @throws ProtocolUnavailableException when no segment for the release provides the port, the providing segment
	 * failed its linkage self-check, or more than one adapter is provided; the worker reports the binding's
	 * capability as unavailable with the exception's message
	 */
	default <P> @NotNull P adapter(@NotNull Class<P> port) {
		throw new ProtocolUnavailableException("This player binding context provides no adapter for " + port.getName());
	}

	/**
	 * Returns the Minecraft version of the player's client, the one the player was created for, which the host's
	 * capability context names as its client version. A binding looks up what differs between versions by it, such
	 * as the block data of the version or how its client moves.
	 *
	 * <pre>{@code
	 * public WorkerBinding bind(PlayerBindingContext<Object> player, OperationRegistry operations) {
	 *     boolean playerInput = player.minecraftVersion().isAtLeast(MinecraftVersion.parse("1.21.2"));
	 *     ...
	 * }
	 * }</pre>
	 *
	 * <p>A context outside a library worker, such as a test double, has no client; this default reports that.</p>
	 *
	 * @return the client's Minecraft version
	 * @throws ProtocolUnavailableException when the context has no client version; the worker reports the
	 * binding's capability as unavailable with the exception's message
	 */
	default @NotNull MinecraftVersion minecraftVersion() {
		throw new ProtocolUnavailableException("This player binding context has no client version");
	}
}
