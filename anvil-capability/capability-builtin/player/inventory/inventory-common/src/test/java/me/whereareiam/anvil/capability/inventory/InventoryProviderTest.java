package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
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

class InventoryProviderTest {
	@Test
	void describesTheInventoryCapabilityForTheLibrariesWithAWorkerSide() {
		InventoryProvider provider = new InventoryProvider();

		assertEquals("me.whereareiam.anvil.inventory", provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of("mcprotocol"), provider.supportedLibraries());
		assertEquals(Inventory.class, provider.capability());
	}

	@Test
	void requestsThroughThePlayersChannelOnlyOnceTheInventoryIsUsed() {
		RecordingChannel channel = new RecordingChannel();
		Inventory inventory = new InventoryProvider().create(new ChannelContext(channel));
		assertTrue(channel.requests.isEmpty());
		assertEquals(List.of(InventoryOperations.CHANGED.getId()), channel.subscriptions);

		inventory.selectSlot(0);
		inventory.click(4, InventoryClick.LEFT, 0);

		assertEquals(List.of(InventoryOperations.SELECT.getId(), InventoryOperations.CLICK.getId()), channel.requests);
		assertEquals(List.of(new SlotSelection(0), new InventoryAction(4, InventoryClick.LEFT, 0)), channel.payloads);
	}

	/**
	 * Records each request's operation and payload and each subscribed event.
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
			throw new AssertionError("The test does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(InventoryProvider.ID);
		}
	}

	/**
	 * Supplies only the channel; inventory needs no services, capabilities or observations.
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
			throw new AssertionError("Inventory does not observe the player");
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			throw new AssertionError("Inventory registers no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}
