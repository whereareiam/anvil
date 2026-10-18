package me.whereareiam.anvil.capability.messages.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The lines of chat a player can still scroll back to, modelled on the vanilla client's chat. The client keeps the
 * chat messages it receives while it has a player, from the server's login until a return to configuration, never
 * action-bar text, as lines, and only the newest {@value #MAX_LINES} of them: it splits each message at its line breaks
 * and wraps each part at the chat's width. A message the server deletes is
 * replaced by the client's deleted marker, {@value #DELETED_MARKER}, and a new connection starts with an empty chat.
 *
 * <p>The model splits at line breaks but cannot wrap, because wrapping depends on the client's font, chat width and
 * scale. Wrapping only adds lines, so the model never keeps fewer old lines than a real client:</p>
 * <ul>
 * <li>a line missing from the model is gone from every vanilla client's chat, however wide;</li>
 * <li>a line the model holds is still in the chat of a client wide enough not to wrap the lines after it, but a
 * narrow chat may already have pushed it out.</li>
 * </ul>
 *
 * <p>So the model proves that a line is gone, such as earlier chat after a server pushed {@value #MAX_LINES} blank
 * lines, but not that a line is still there on every client.</p>
 *
 * <pre>{@code
 * MessageWatch chat = messages.watch();
 * messages.command("clearchat");
 * chat.received("Chat cleared");
 * assertFalse(messages.chatLines().contains("secret"));
 * }</pre>
 */
@Value
public class ChatLines {
	/**
	 * Number of lines the vanilla client keeps in its chat.
	 */
	public static final int MAX_LINES = 100;
	/**
	 * The plain text of the line the client shows instead of a message the server deleted, its translation key.
	 */
	public static final String DELETED_MARKER = "chat.deleted_marker";

	/**
	 * The lines, oldest first, at most {@value #MAX_LINES} of them.
	 */
	@NotNull List<String> lines;

	/**
	 * Creates the chat from a copy of its lines.
	 *
	 * @param lines the lines, oldest first
	 */
	public ChatLines(@NotNull List<String> lines) {
		this.lines = List.copyOf(lines);
	}

	/**
	 * Tells whether a line contains the supplied text.
	 *
	 * @param text required line content
	 * @return whether any line contains it
	 */
	public boolean contains(@NotNull String text) {
		return lines.stream().anyMatch(line -> line.contains(text));
	}
}
