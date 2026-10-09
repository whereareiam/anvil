package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.exception.NativeAdapterUnavailableException;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Function;

/**
 * Player lifecycle and typed access to the native session of an external protocol library.
 * Native sessions represent individual connection generations; callers must not retain
 * them across reconnects. Capability bindings, in contrast, live until player destruction.
 *
 * @param <S> native session type of the external library
 */
public interface NativePlayer<S> {
	/**
	 * Returns the authenticated player name.
	 * @return player name
	 */
	@NotNull String name();
	/**
	 * Returns the client profile identity.
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
	@NotNull ProtocolSubscription bindNativeSession(@NotNull Function<S, ProtocolSubscription> listener);
	/**
	 * Returns the latest protocol view yaw, including server corrections.
	 * @return yaw in degrees
	 */
	float yaw();
	/**
	 * Returns the latest protocol view pitch, including server corrections.
	 * @return pitch in degrees
	 */
	float pitch();
	/**
	 * Records view direction transmitted by a native operation.
	 * @param yaw yaw in degrees
	 * @param pitch pitch in degrees
	 */
	void view(float yaw, float pitch);
	/**
	 * Emits an encoded named message without interpreting its capability schema.
	 * @param event namespaced event ID
	 * @param payload encoded event bytes
	 */
	void emit(@NotNull String event, byte @NotNull [] payload);

	/**
	 * Returns the adapter of a library-neutral port for the worker's loaded release: the one implementation that
	 * the segment selected for the release provides. Adapters are stateless, so a worker may hand the same instance
	 * to every player.
	 *
	 * @param port port interface the adapter implements
	 * @param <P> port type
	 * @return the adapter
	 * @throws NativeAdapterUnavailableException when no segment selected for the release provides the port, the
	 * providing segment failed its linkage self-check against the loaded release, or more than one adapter is
	 * provided; its message names the port and, where known, the segment and the member that does not link
	 */
	<P> @NotNull P adapter(@NotNull Class<P> port);
}
