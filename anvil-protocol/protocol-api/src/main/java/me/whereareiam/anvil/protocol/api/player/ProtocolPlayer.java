package me.whereareiam.anvil.protocol.api.player;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Backend-owned protocol client before dependency-discovered capabilities are composed around it.
 */
public interface ProtocolPlayer {
	/**
	 * Returns the configured player name.
	 *
	 * @return player name
	 */
	@NotNull String name();

	/**
	 * Returns the resolved native client version.
	 *
	 * @return Minecraft client version
	 */
	@NotNull String clientVersion();

	/**
	 * Returns the client identity established by the protocol backend.
	 *
	 * @return immutable client identity
	 */
	@NotNull PlayerIdentity identity();

	/**
	 * Finds an implementation-specific service that capability providers may target through a stable API.
	 *
	 * @param type service type
	 * @param <T> service type
	 * @return matching service when supported
	 */
	@NotNull <T> Optional<T> findService(@NotNull Class<T> type);

	/**
	 * Reports whether this backend-owned player was permanently destroyed.
	 *
	 * @return destruction state
	 */
	boolean destroyed();

	/**
	 * Permanently releases the protocol client and its worker registration.
	 */
	void destroy();
}
