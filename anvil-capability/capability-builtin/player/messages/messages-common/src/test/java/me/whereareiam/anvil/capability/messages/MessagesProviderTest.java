package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesProviderTest {
	@Test
	void describesTheMessagesCapabilityForTheLibrariesWithAWorkerSide() {
		MessagesProvider provider = new MessagesProvider();

		assertEquals("me.whereareiam.anvil.messages", MessagesProvider.ID);
		assertEquals(MessagesProvider.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of("mcprotocol"), provider.supportedLibraries());
		assertEquals(Messages.class, provider.capability());
	}

	@Test
	void observesReceivedMessagesAndSendsThroughThePlayersChannelWithoutRequestingOnCreation() {
		RecordingChannel channel = new RecordingChannel();
		Messages messages = new MessagesProvider().create(new ChannelContext(channel));
		assertEquals(List.of(MessagesOperations.RECEIVED.getId()), channel.subscriptions);
		assertTrue(channel.requests.isEmpty());

		messages.chat("hello");
		messages.command("/list");

		assertEquals(List.of(MessagesOperations.CHAT.getId(), MessagesOperations.COMMAND.getId()), channel.requests);
		assertEquals(List.of(new MessageText("hello"), new MessageText("list")), channel.payloads);
	}

	/**
	 * Records requests and subscriptions without delivering events.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> requests = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private final List<String> subscriptions = new ArrayList<>();

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
			payloads.add(request);
			return null;
		}

		@Override
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			subscriptions.add(eventDescriptor.getId());
			return () -> { };
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			throw new AssertionError("Creating messages does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(MessagesProvider.ID);
		}
	}

	/**
	 * Supplies only the channel; messages need no services, capabilities or observations.
	 */
	private record ChannelContext(CapabilityChannel channel) implements ProtocolPlayerCapabilityContext {
		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			return Optional.empty();
		}

		@Override
		public @NotNull String playerName() {
			return "Alice";
		}

		@Override
		public @NotNull String clientVersion() {
			return "1.21.11";
		}

		@Override
		public @NotNull PlayerObservation observation() {
			throw new AssertionError("Messages do not observe the player");
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			throw new AssertionError("Messages register no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}
