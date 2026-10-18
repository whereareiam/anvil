package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.messages.model.ChatLines;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBufferTest {
	@Test
	void splitsEachMessageAtEveryLineBreakAsTheClientDoes() {
		ChatBuffer chat = new ChatBuffer();

		chat.add(0, "first\nsecond");
		chat.add(1, "");
		chat.add(2, "trailing\n");
		chat.add(3, "\n");

		assertEquals(List.of("first", "second", "", "trailing", "", "", ""), chat.lines().getLines());
	}

	@Test
	void keepsOnlyTheNewestHundredLines() {
		ChatBuffer chat = new ChatBuffer();

		chat.add(0, "oldest\nkept");
		IntStream.range(1, ChatLines.MAX_LINES).forEach(index -> chat.add(index, "line " + index));

		List<String> lines = chat.lines().getLines();
		assertEquals(ChatLines.MAX_LINES, lines.size());
		assertEquals("kept", lines.getFirst());
		assertEquals("line 99", lines.getLast());
		assertFalse(chat.lines().contains("oldest"));
	}

	@Test
	void pushesEveryEarlierLineOutWithAHundredBlankLines() {
		ChatBuffer chat = new ChatBuffer();
		chat.add(0, "secret\nhistory");
		chat.add(1, "more history");

		IntStream.range(2, 2 + ChatLines.MAX_LINES).forEach(index -> chat.add(index, ""));

		assertEquals(Collections.nCopies(ChatLines.MAX_LINES, ""), chat.lines().getLines());
		assertFalse(chat.lines().contains("history"));
	}

	@Test
	void pushesEveryEarlierLineOutWithOneMessageOfAHundredBlankLines() {
		ChatBuffer chat = new ChatBuffer();
		chat.add(0, "secret");

		chat.add(1, "\n".repeat(ChatLines.MAX_LINES - 1));

		assertEquals(Collections.nCopies(ChatLines.MAX_LINES, ""), chat.lines().getLines());
	}

	@Test
	void replacesADeletedMessageWithTheDeletedMarker() {
		ChatBuffer chat = new ChatBuffer();
		chat.add(0, "hello");
		chat.add(1, "insult\nspread\nover lines");
		chat.add(2, "bye");

		chat.delete(1);
		chat.delete(1);
		chat.delete(7);

		assertEquals(List.of("hello", ChatLines.DELETED_MARKER, "bye"), chat.lines().getLines());
	}

	@Test
	void bringsBackOlderLinesWhenADeletionShortensAMessage() {
		ChatBuffer chat = new ChatBuffer();
		chat.add(0, "oldest");
		chat.add(1, "\n".repeat(ChatLines.MAX_LINES - 1));
		assertFalse(chat.lines().contains("oldest"));

		chat.delete(1);

		assertEquals(List.of("oldest", ChatLines.DELETED_MARKER), chat.lines().getLines());
	}

	@Test
	void forgetsMessagesBeyondTheHundredItRebuildsFrom() {
		ChatBuffer chat = new ChatBuffer();
		chat.add(0, "forgotten");
		IntStream.range(1, ChatBuffer.MAX_MESSAGES + 1).forEach(index -> chat.add(index, "message " + index));

		chat.delete(ChatBuffer.MAX_MESSAGES);
		chat.delete(ChatBuffer.MAX_MESSAGES - 1);

		List<String> expected = new ArrayList<>(IntStream.range(1, ChatBuffer.MAX_MESSAGES - 1)
				.mapToObj(index -> "message " + index).toList());
		expected.add(ChatLines.DELETED_MARKER);
		expected.add(ChatLines.DELETED_MARKER);
		assertEquals(expected, chat.lines().getLines());
	}

	@Test
	void startsEmptyAgainWhenCleared() {
		ChatBuffer chat = new ChatBuffer();
		chat.add(0, "before");

		chat.clear();
		chat.add(1, "after");

		assertEquals(List.of("after"), chat.lines().getLines());
		assertTrue(chat.lines().contains("aft"));
	}
}
