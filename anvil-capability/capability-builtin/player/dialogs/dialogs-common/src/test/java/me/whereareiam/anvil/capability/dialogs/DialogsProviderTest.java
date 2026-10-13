package me.whereareiam.anvil.capability.dialogs;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.commands.Commands;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
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
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogsProviderTest {
	@Test
	void describesTheDialogsCapabilityForTheLibrariesWithAWorkerSide() {
		DialogsProvider provider = new DialogsProvider();

		assertEquals("me.whereareiam.anvil.dialogs", DialogsProvider.ID);
		assertEquals(DialogsProvider.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(Commands.class), provider.descriptor().getOptionalCapabilities());
		assertEquals(Set.of("mcprotocol"), provider.supportedLibraries());
		assertEquals(Dialogs.class, provider.capability());
	}

	@Test
	void observesShownAndClearedDialogsWithoutRequestingOnCreation() {
		RecordingChannel channel = new RecordingChannel();
		Dialogs dialogs = new DialogsProvider().create(new ChannelContext(channel));

		assertEquals(List.of(DialogsOperations.REGISTRY.getId(), DialogsOperations.SHOWN.getId(), DialogsOperations.CLEARED.getId(),
				DialogsOperations.RESET.getId()), channel.subscriptions);
		assertTrue(channel.requests.isEmpty());
		assertTrue(dialogs.current().isEmpty());
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
			throw new AssertionError("Creating dialogs does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(DialogsProvider.ID);
		}
	}

	private static final Commands COMMANDS = new Commands() {
		@Override
		public @NotNull Optional<CommandTree> tree() {
			return Optional.empty();
		}

		@Override
		public @NotNull CommandTree tree(@NotNull Predicate<CommandTree> matcher, @NotNull Duration timeout) {
			throw new AssertionError("Creating dialogs does not wait");
		}

		@Override
		public @NotNull CommandCheck check(@NotNull String command) {
			throw new AssertionError("Creating dialogs checks no command");
		}
	};

	/**
	 * Supplies the channel and the player's commands; dialogs need no services or observations.
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
			throw new AssertionError("Dialogs do not observe the player");
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			throw new AssertionError("Dialogs register no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return type == Commands.class ? Optional.of(type.cast(COMMANDS)) : Optional.empty();
		}
	}
}
