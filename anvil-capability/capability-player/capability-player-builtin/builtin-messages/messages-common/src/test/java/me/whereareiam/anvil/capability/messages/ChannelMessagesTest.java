package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.ChatLines;
import me.whereareiam.anvil.capability.messages.model.ChatMessage;
import me.whereareiam.anvil.capability.messages.model.DroppedChat;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.messages.type.ChatDropReason;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelMessagesTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	@Test
	void requestsChatAsTyped() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);

		messages.chat("/not a command");

		assertEquals(List.of("messages.chat"), channel.requests);
		assertEquals(List.of(new MessageText("/not a command")), channel.payloads);
	}

	@Test
	void requestsCommandsWithoutTheirLeadingSlash() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);

		messages.command("/anvil-fixture ping");
		messages.command("list");

		assertEquals(List.of("messages.command", "messages.command"), channel.requests);
		assertEquals(List.of(new MessageText("anvil-fixture ping"), new MessageText("list")), channel.payloads);
	}

	@Test
	void keepsEveryMessageTheChatShowedInOrder() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		assertTrue(messages.history().isEmpty());

		drop(channel, "anvil:configuring", ChatDropReason.NO_PLAYER);
		show(channel, 0, "anvil:welcome");
		channel.publish(MessagesChannel.CHAT_CLEARED, null);
		show(channel, 1, "anvil:pong");

		assertEquals(List.of("anvil:welcome", "anvil:pong"), messages.history());
		assertThrows(UnsupportedOperationException.class, () -> messages.history().add("changed"));
	}

	@Test
	void keepsTheChatTheWorkerReports() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);

		show(channel, 0, "before");
		channel.publish(MessagesChannel.CHAT_CLEARED, null);
		show(channel, 1, "hello\nthere");
		show(channel, 2, "insult");
		channel.publish(MessagesChannel.CHAT_DELETED, 2L);

		assertEquals(new ChatLines(List.of("hello", "there", ChatLines.DELETED_MARKER)), messages.chatLines());
	}

	@Test
	void returnsTheLatestShownMessageContainingTheText() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		show(channel, 0, "anvil:pong 1");
		show(channel, 1, "anvil:welcome");
		show(channel, 2, "anvil:pong 2");

		assertEquals("anvil:pong 2", messages.received("pong", TIMEOUT));
		assertEquals("anvil:welcome", messages.received("welcome"));
	}

	@Test
	void waitsThroughTheChannelUntilTheChatShowsTheText() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		drop(channel, "anvil:ping", ChatDropReason.NO_PLAYER);

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> messages.received("pong", TIMEOUT));

		assertEquals("Player did not see a message containing 'pong' in chat", failure.getMessage());
	}

	@Test
	void watchesOnlyMessagesShownSinceItAndContinuesAfterEachMatch() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		show(channel, 0, "Use /login");
		MessageWatch chat = messages.watch();

		assertEquals("Player did not see a matching message in chat", assertThrows(IllegalStateException.class,
				() -> chat.received("/login", TIMEOUT)).getMessage());

		show(channel, 1, "Invalid password");
		show(channel, 2, "Use /login");
		show(channel, 3, "Use /login");
		channel.publish(MessagesChannel.CHAT_DELETED, 3L);
		assertEquals(List.of("Invalid password", "Use /login", "Use /login"), chat.unread());
		assertEquals("Use /login", chat.received("/login", TIMEOUT));
		assertEquals(List.of("Use /login"), chat.unread());
		assertEquals("Use /login", chat.received("/login", TIMEOUT));
		assertThrows(IllegalStateException.class, () -> chat.received("/login", TIMEOUT));
		assertEquals(List.of(), messages.watch().unread());
	}

	@Test
	void expectsAMatchingMessageOnlySinceTheWatchStarted() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		show(channel, 0, "Invalid code");
		MessageWatch chat = messages.watch();
		Expectation invalid = chat.expectReceived(text -> text.contains("Invalid code"));

		assertFalse(invalid.met());
		assertEquals("see a matching message in chat", invalid.getDescription());

		show(channel, 1, "Invalid code");
		assertTrue(invalid.met());
		assertEquals("Invalid code", chat.received(text -> text.contains("Invalid code"), TIMEOUT));
	}

	@Test
	void failsWhenTheChatShowsAForbiddenMessageSinceThePreviousWaitOnly() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		show(channel, 0, "Invalid password.");
		MessageWatch chat = messages.watch();

		drop(channel, "Invalid password.", ChatDropReason.NO_PLAYER);
		chat.neverReceived("Invalid", TIMEOUT);

		show(channel, 1, "Invalid password.");
		assertEquals("Player saw a message in chat it must not see: Invalid password.", assertThrows(IllegalStateException.class,
				() -> chat.neverReceived("Invalid", TIMEOUT)).getMessage());

		show(channel, 2, "Welcome back");
		chat.received("Welcome", TIMEOUT);
		chat.neverReceived("Invalid", TIMEOUT);
	}

	@Test
	void namesTheMatchingMessagesTheChatLeftOutWhenAWaitFails() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		drop(channel, "Use /login", ChatDropReason.NO_PLAYER);
		MessageWatch chat = messages.watch();
		drop(channel, "Use /register", ChatDropReason.NO_PLAYER);
		drop(channel, "hidden /register", ChatDropReason.FULLY_FILTERED);
		drop(channel, "unrelated", ChatDropReason.NO_PLAYER);

		assertEquals("""
				Player did not see a matching message in chat
				The chat left out matching messages that reached the client: 'Use /register' (the client had no player), \
				'hidden /register' (the server's filter hid it)""", assertThrows(IllegalStateException.class,
				() -> chat.received("/register", TIMEOUT)).getMessage());
		assertEquals("Player did not see a matching message in chat", assertThrows(IllegalStateException.class,
				() -> chat.received("/login", TIMEOUT)).getMessage());
		assertEquals("""
				Player did not see a message containing '/login' in chat
				The chat left out matching messages that reached the client: 'Use /login' (the client had no player)""",
				assertThrows(IllegalStateException.class, () -> messages.received("/login", TIMEOUT)).getMessage());

		show(channel, 0, "Use /register");
		chat.received("/register", TIMEOUT);
		assertEquals("Player did not see a matching message in chat", assertThrows(IllegalStateException.class,
				() -> chat.received("/register", TIMEOUT)).getMessage());
		assertEquals(List.of("Use /register"), messages.history());
	}

	private static void show(RecordingChannel channel, long id, String text) {
		channel.publish(MessagesChannel.CHAT_ADDED, new ChatMessage(id, text));
	}

	private static void drop(RecordingChannel channel, String text, ChatDropReason reason) {
		channel.publish(MessagesChannel.CHAT_DROPPED, new DroppedChat(text, reason));
	}

	/**
	 * Records requests and delivers published events synchronously; a wait whose condition does not hold fails
	 * immediately instead of timing out.
	 */
	private static final class RecordingChannel implements ProtocolChannel {
		private final List<String> requests = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
			requests.add(operation.getId());
			payloads.add(request);
			return null;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(MessagesChannel.ID);
		}
	}
}
