package me.whereareiam.anvil.capability.protocol.api.player.worker;

import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * Native lifecycle controls, SDK access, rotation, and event delivery supplied to one player's binding.
 * This context remains associated with that player until destruction. SDK values returned by
 * {@link #backend()} and supplied to {@link #bindBackend(Function)} represent individual connection
 * generations; callers must not retain them across reconnects.
 *
 * @param <B> actual external SDK context type
 */
public interface PlayerBindingContext<B> {
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
	 * Returns the current connected SDK context.
	 *
	 * @return native session
	 * @throws IllegalStateException when the player is not connected
	 */
	@NotNull B backend();

	/**
	 * Tests whether a callback still belongs to the current native connection generation.
	 * Listeners use this check to ignore callbacks already in flight during a reconnect.
	 *
	 * @param backend native context captured when the listener was installed
	 * @return whether the context is still current
	 */
	boolean isCurrentBackend(@NotNull B backend);

	/**
	 * Binds native listeners before each session starts connecting. Previous listener bindings
	 * are closed before a replacement is installed; closing the returned subscription also
	 * closes its current binding. The callback must not start the native session itself.
	 *
	 * @param listener factory returning cleanup for the supplied native generation
	 * @return owned generation-listener registration
	 */
	@NotNull Subscription bindBackend(@NotNull Function<B, Subscription> listener);

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
}
