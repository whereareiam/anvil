package me.whereareiam.anvil.protocol.adapter.api.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Stable worker-side player surface used by packet-oriented capabilities.
 */
public interface ProtocolWorkerPlayer {
	/**
	 * Returns the authenticated protocol username.
	 *
	 * @return username
	 */
	@NotNull String name();

	/**
	 * Returns the client profile UUID.
	 *
	 * @return client UUID
	 */
	@NotNull UUID uuid();

	/**
	 * Returns the worker's shared JSON mapper.
	 *
	 * @return JSON mapper
	 */
	@NotNull ObjectMapper mapper();

	/**
	 * Starts the underlying login sequence.
	 */
	void connect();

	/**
	 * Disconnects the current protocol session.
	 */
	void disconnect();

	/**
	 * Replaces the current protocol session and starts login again.
	 */
	void rejoin();

	/**
	 * Sends one backend-owned packet on the current session.
	 *
	 * @param packet opaque backend packet
	 */
	void send(@NotNull Object packet);

	/**
	 * Emits a namespaced event to the host-side player.
	 *
	 * @param event event name
	 * @param payload event payload writer
	 */
	void emit(@NotNull String event, @NotNull Consumer<ObjectNode> payload);

	/**
	 * Returns or creates player-scoped capability state.
	 *
	 * @param key state type used as a collision-free key
	 * @param factory initial state factory
	 * @param <T> state type
	 * @return player-scoped state
	 */
	@NotNull <T> T state(@NotNull Class<T> key, @NotNull Supplier<T> factory);

	/**
	 * Returns the latest protocol yaw.
	 *
	 * @return yaw
	 */
	float yaw();

	/**
	 * Returns the latest protocol pitch.
	 *
	 * @return pitch
	 */
	float pitch();

	/**
	 * Updates the shared player view used by movement and interaction capabilities.
	 *
	 * @param yaw yaw
	 * @param pitch pitch
	 */
	void view(float yaw, float pitch);

	/**
	 * Allocates the next interaction sequence number.
	 *
	 * @return next sequence
	 */
	int nextSequence();
}
