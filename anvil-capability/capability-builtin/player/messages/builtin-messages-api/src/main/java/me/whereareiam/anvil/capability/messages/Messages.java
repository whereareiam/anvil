package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Sends player chat and commands and observes messages received from the server.
 */
public interface Messages extends PlayerCapability {
	/**
	 * Sends a public chat message.
	 *
	 * @param message message without a command prefix
	 */
	void chat(@NotNull String message);

	/**
	 * Sends a server command as the player.
	 *
	 * @param command command with or without a leading slash
	 */
	void command(@NotNull String command);

	/**
	 * Returns an immutable snapshot of captured messages without waiting.
	 *
	 * @return captured message history
	 */
	@NotNull List<String> history();

	/**
	 * Waits for a captured message containing the supplied text using the default timeout.
	 *
	 * @param text required message content
	 * @return matching message
	 */
	default @NotNull String received(@NotNull String text) {
		return received(text, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for a captured message containing the supplied text.
	 *
	 * @param text required message content
	 * @param timeout maximum wait
	 * @return matching message
	 */
	@NotNull String received(@NotNull String text, @NotNull Duration timeout);
}
