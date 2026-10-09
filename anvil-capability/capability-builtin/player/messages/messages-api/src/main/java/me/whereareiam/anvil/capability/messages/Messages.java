package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.messages.model.ReceivedMessage;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

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

	/**
	 * Captures the current end of the history. A wait that starts from a checkpoint ignores every message
	 * received before it, so a text that repeats, such as a prompt shown again after a reconnect, is not
	 * satisfied by its earlier occurrence.
	 *
	 * <pre>{@code
	 * int before = messages.checkpoint();
	 * messages.command("login secret");
	 * ReceivedMessage welcome = messages.received(text -> text.contains("Welcome back"), before, timeout);
	 * messages.notReceived(text -> text.contains("Invalid password"), welcome.getCheckpoint(), Duration.ofSeconds(2));
	 * }</pre>
	 *
	 * @return number of messages received so far
	 */
	int checkpoint();

	/**
	 * Waits for the first message after a checkpoint that satisfies a condition.
	 *
	 * @param matcher condition on the message's plain text
	 * @param after checkpoint; only messages received after it are considered
	 * @param timeout maximum wait
	 * @return the matching message and the checkpoint directly after it
	 */
	@NotNull ReceivedMessage received(@NotNull Predicate<String> matcher, int after, @NotNull Duration timeout);

	/**
	 * Expects that no message after a checkpoint satisfies a condition for a duration. It fails as soon as such a
	 * message arrives, naming the message, and returns normally once the duration has passed without one.
	 *
	 * @param matcher condition on the message's plain text
	 * @param after checkpoint; only messages received after it are considered
	 * @param duration how long no matching message may arrive
	 */
	void notReceived(@NotNull Predicate<String> matcher, int after, @NotNull Duration duration);
}
