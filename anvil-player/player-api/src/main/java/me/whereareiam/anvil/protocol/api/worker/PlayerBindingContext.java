package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * Native lifecycle controls, session access, rotation, and event delivery supplied to one player's binding.
 * This context remains associated with that player until destruction. Native sessions returned by
 * {@link #nativeSession()} and supplied to {@link #bindNativeSession(Function)} represent individual
 * connection generations; callers must not retain them across reconnects.
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
	 * segment selected for the release provides. Capability wiring obtains every release-specific implementation
	 * this way, never through a service lookup of its own, so the worker's segment selection and linkage self-check
	 * decide what it receives. Call it while binding, not in the extension's constructor.
	 *
	 * <pre>{@code
	 * public WorkerBinding bind(PlayerBindingContext<Object> player, OperationRegistry operations) {
	 *     InventoryPackets<?> packets = player.adapter(InventoryPackets.class);
	 *     return new InventoryBinding<>(packets).bind(player, operations);
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
}
