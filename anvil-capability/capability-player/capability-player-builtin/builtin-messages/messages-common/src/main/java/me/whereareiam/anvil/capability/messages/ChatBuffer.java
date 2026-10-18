package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.messages.model.ChatLines;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * The vanilla client's chat without wrapping. Like the client, it keeps the newest {@value #MAX_MESSAGES} messages and
 * shows the newest {@value ChatLines#MAX_LINES} of their lines, so a deletion that shortens a message brings back
 * older lines of the kept messages, as the client's rebuild does. Each message is split at every line break, so a
 * message ending in one, or an empty message, contributes an empty line. The client delays a deletion until the
 * message is three seconds old; the buffer applies it at once, which never leaves fewer old lines than the client.
 */
final class ChatBuffer {
	/**
	 * Number of messages the vanilla client keeps to rebuild its lines from.
	 */
	static final int MAX_MESSAGES = 100;
	private static final long DELETED = -1L;

	private final List<Entry> messages = new ArrayList<>();

	synchronized void add(long id, @NotNull String text) {
		messages.add(new Entry(id, List.of(text.split("\n", -1))));
		if (messages.size() > MAX_MESSAGES) messages.removeFirst();
	}

	synchronized void delete(long id) {
		for (ListIterator<Entry> entries = messages.listIterator(); entries.hasNext(); ) {
			if (entries.next().id() != id) continue;

			entries.set(new Entry(DELETED, List.of(ChatLines.DELETED_MARKER)));
			return;
		}
	}

	synchronized void clear() {
		messages.clear();
	}

	synchronized @NotNull ChatLines lines() {
		List<String> lines = messages.stream().flatMap(message -> message.lines().stream()).toList();

		return new ChatLines(lines.subList(Math.max(0, lines.size() - ChatLines.MAX_LINES), lines.size()));
	}

	private record Entry(long id, List<String> lines) {
	}
}
