package me.whereareiam.anvil.capability.server;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Exposes client, proxy, and backend identity observed for a simulated player.
 */
public interface Server extends PlayerCapability {
	/**
	 * Returns the currently observed identity immediately without waiting.
	 *
	 * @return current identity snapshot
	 */
	@NotNull PlayerIdentity identity();

	/**
	 * Waits until a named server observes the player using the default timeout.
	 *
	 * @param server scenario server name
	 * @return identity observed on the server
	 */
	default @NotNull PlayerIdentity joined(@NotNull String server) {
		return joined(server, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until a named server observes the player.
	 *
	 * @param server scenario server name
	 * @param timeout maximum wait
	 * @return identity observed on the server
	 */
	@NotNull PlayerIdentity joined(@NotNull String server, @NotNull Duration timeout);

	/**
	 * Expects the player to stay on a server for a duration. It fails as soon as the player is observed
	 * elsewhere or nowhere, naming where, and returns normally once the duration has passed on that server.
	 *
	 * @param server expected current server name
	 * @param duration how long the player must stay
	 */
	void stayed(@NotNull String server, @NotNull Duration duration);
}
