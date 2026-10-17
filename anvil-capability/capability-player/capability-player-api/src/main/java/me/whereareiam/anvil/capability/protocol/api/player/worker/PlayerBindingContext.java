package me.whereareiam.anvil.capability.protocol.api.player.worker;

import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
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
	 * Binds native listeners before each session starts connecting. Previous listener bindings
	 * are closed before a replacement is installed; closing the returned subscription also
	 * closes its current binding. The callback must not start the native session itself.
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
	 * Emits a typed player event to subscribed host capabilities.
	 *
	 * @param eventDescriptor registered event schema
	 * @param payload event value, or {@code null} for a {@link Void} event
	 * @param <E> event payload type
	 */
	<E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload);

	/**
	 * Returns the adapter of a library-neutral port for this player's library release: the implementation that the
	 * segment selected for the release provides. Capability wiring obtains every release-specific implementation
	 * this way, never through a service lookup of its own, so the worker's segment selection and linkage self-check
	 * decide what it receives. Call it while binding, not in the extension's constructor.
	 *
	 * <pre>{@code
	 * public WorkerBinding bind(PlayerBindingContext<Object> player, OperationRegistry operations) {
	 *     MovementPackets<?> packets = player.adapter(MovementPackets.class);
	 *     return new MovementBinding<>(packets).bind(player, operations);
	 * }
	 * }</pre>
	 *
	 * <p>A context outside a library worker, such as a test double, provides no adapters; this default reports
	 * that.</p>
	 *
	 * @param port port interface the adapter implements
	 * @param <P> port type
	 * @return the adapter, which is stateless and may serve other players of the same worker
	 * @throws AdapterUnavailableException when no segment for the release provides the port, the providing segment
	 * failed its linkage self-check, or more than one adapter is provided; the worker reports the binding's
	 * capability as unavailable with the exception's message
	 */
	default <P> @NotNull P adapter(@NotNull Class<P> port) {
		throw new AdapterUnavailableException("This player binding context provides no adapter for " + port.getName());
	}
}
