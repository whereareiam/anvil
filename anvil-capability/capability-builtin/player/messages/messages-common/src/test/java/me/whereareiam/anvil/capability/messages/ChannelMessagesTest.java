package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
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
	void keepsEveryReceivedMessageInOrder() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		assertTrue(messages.history().isEmpty());

		channel.publish(MessagesOperations.RECEIVED, new MessageText("anvil:welcome"));
		channel.publish(MessagesOperations.RECEIVED, new MessageText("anvil:pong"));

		assertEquals(List.of("anvil:welcome", "anvil:pong"), messages.history());
		assertThrows(UnsupportedOperationException.class, () -> messages.history().add("changed"));
	}

	@Test
	void returnsTheLatestReceivedMessageContainingTheText() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		channel.publish(MessagesOperations.RECEIVED, new MessageText("anvil:pong 1"));
		channel.publish(MessagesOperations.RECEIVED, new MessageText("anvil:welcome"));
		channel.publish(MessagesOperations.RECEIVED, new MessageText("anvil:pong 2"));

		assertEquals("anvil:pong 2", messages.received("pong", TIMEOUT));
		assertEquals("anvil:welcome", messages.received("welcome"));
	}

	@Test
	void waitsThroughTheChannelUntilAMessageContainsTheText() {
		RecordingChannel channel = new RecordingChannel();
		ChannelMessages messages = new ChannelMessages(channel);
		channel.publish(MessagesOperations.RECEIVED, new MessageText("anvil:welcome"));

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> messages.received("pong", TIMEOUT));

		assertEquals("Player did not receive a message containing 'pong'", failure.getMessage());
	}

	/**
	 * Records requests and delivers published events synchronously; a wait whose condition does not hold fails
	 * immediately instead of timing out.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> requests = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
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
			return Set.of(MessagesProvider.ID);
		}
	}
}
