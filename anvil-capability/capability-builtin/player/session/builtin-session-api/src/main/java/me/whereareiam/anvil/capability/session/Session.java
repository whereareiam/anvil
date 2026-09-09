package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.session.model.SessionState;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Controls and observes a simulated player's current protocol session.
 */
public interface Session extends PlayerCapability {
	/**
	 * Returns current connection and kick state immediately.
	 *
	 * @return immutable session state
	 */
	@NotNull SessionState state();

	/**
	 * Starts the Minecraft login sequence.
	 */
	void connect();

	/**
	 * Disconnects the current network session.
	 */
	void disconnect();

	/**
	 * Creates a fresh network session and starts login again.
	 */
	void rejoin();

	/**
	 * Waits for the player to complete login using the default timeout.
	 */
	default void connected() {
		connected(SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the player to complete login.
	 *
	 * @param timeout maximum wait
	 */
	void connected(@NotNull Duration timeout);

	/**
	 * Waits for the current session to disconnect using the default timeout.
	 */
	default void disconnected() {
		disconnected(SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the current session to disconnect.
	 *
	 * @param timeout maximum wait
	 */
	void disconnected(@NotNull Duration timeout);

	/**
	 * Waits for the server to kick the current session using the default timeout.
	 *
	 * @return actual plain-text kick reason
	 */
	default @NotNull String kicked() {
		return kicked(SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the server to kick the current session.
	 *
	 * @param timeout maximum wait
	 * @return actual plain-text kick reason
	 */
	@NotNull String kicked(@NotNull Duration timeout);
}
