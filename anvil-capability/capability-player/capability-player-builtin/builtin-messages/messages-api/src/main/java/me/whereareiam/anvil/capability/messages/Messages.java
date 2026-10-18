package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.messages.model.ChatLines;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Sends player chat and commands and observes the chat messages the player sees.
 *
 * <p>The history holds every message the player's chat showed, in order and across reconnects, flattened to plain
 * text: player and system chat and, where the protocol has it, disguised chat, by the vanilla client's rules. The
 * client's chat leaves out player chat the server's filter hides completely and every message that arrives while the
 * client has no player: before the server's login, such as while the client configures, and from a return to
 * configuration until the next login. {@link #received(String)} and {@link #watch()} follow this history, and
 * {@link #chatLines()} models what the chat still holds of it.</p>
 *
 * <p>Action-bar text is not chat, even when the server sends it as chat; the screen capability follows it with the
 * other action bars. A message the chat left out is not part of the history, but a wait that fails names the matching
 * ones, so a test can tell a message the server never sent from one the player could not see.</p>
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
	 * Returns an immutable snapshot of the messages the chat showed, without waiting.
	 *
	 * @return the messages the chat showed, oldest first
	 */
	@NotNull List<String> history();

	/**
	 * Waits, using the default timeout, for a message containing the supplied text in the history of what the chat
	 * showed.
	 *
	 * @param text required message content
	 * @return matching message
	 */
	default @NotNull String received(@NotNull String text) {
		return received(text, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for a message containing the supplied text in the history of what the chat showed.
	 *
	 * @param text required message content
	 * @param timeout maximum wait
	 * @return matching message
	 */
	@NotNull String received(@NotNull String text, @NotNull Duration timeout);

	/**
	 * Starts following the messages the player's chat shows from now on. Each watch keeps its own position, so several
	 * can follow the same player independently.
	 *
	 * <pre>{@code
	 * MessageWatch chat = messages.watch();
	 * messages.command("login secret");
	 * chat.received("Welcome back");
	 * chat.neverReceived("Invalid password", Duration.ofSeconds(3));
	 * }</pre>
	 *
	 * @return a watch that starts after the last message the chat showed so far
	 */
	@NotNull MessageWatch watch();


	/**
	 * Returns the lines of chat the player could still scroll back to, without waiting: the messages the chat showed
	 * since the player's latest connection began, split at their line breaks, with the server's deletions applied, and
	 * only the newest {@value ChatLines#MAX_LINES} lines. {@link ChatLines} says what the model guarantees about a real client.
	 *
	 * <pre>{@code
	 * MessageWatch chat = messages.watch();
	 * messages.command("clearchat");
	 * chat.received("Chat cleared");
	 * assertFalse(messages.chatLines().contains("secret"));
	 * }</pre>
	 *
	 * @return the chat's lines now, oldest first
	 */
	@NotNull ChatLines chatLines();
}
