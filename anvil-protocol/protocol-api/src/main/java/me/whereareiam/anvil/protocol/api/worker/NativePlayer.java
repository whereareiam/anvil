package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Function;

/**
 * Player lifecycle and typed access to an external native SDK context.
 * Native contexts represent individual connection generations; callers must not retain
 * them across reconnects. Capability bindings, in contrast, live until player destruction.
 *
 * @param <B> actual external SDK context type
 */
public interface NativePlayer<B> {
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
	 * Returns the current connected SDK context.
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
	@NotNull ProtocolSubscription bindBackend(@NotNull Function<B, ProtocolSubscription> listener);
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
}
