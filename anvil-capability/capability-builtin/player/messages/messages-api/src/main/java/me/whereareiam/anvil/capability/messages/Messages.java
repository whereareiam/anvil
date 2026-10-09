package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Sends player chat and commands and observes messages received from the server.
 *
 * <p>The history holds every chat message the player receives, in order and across reconnects, flattened to plain
 * text: player and system chat and, where the protocol has it, disguised chat. Action-bar text that the server
 * sends as chat, which is game-info chat before Minecraft 1.19 and overlay system chat from 1.19, is part of the
 * history as well, so {@link #received(String)} also matches it. Text sent through the separate title or action-bar
 * packets is not recorded.</p>
 */
public interface Messages extends PlayerCapability {
	/**
	 * Sends a public chat message.
	 *
	 * <p>Before Minecraft 1.19 the protocol has no command packet and commands travel as chat, so the server runs a
	 * message starting with {@code /} as a command. From 1.19 such a message is sent as chat; use
	 * {@link #command(String)} to run a command on every version.</p>
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
