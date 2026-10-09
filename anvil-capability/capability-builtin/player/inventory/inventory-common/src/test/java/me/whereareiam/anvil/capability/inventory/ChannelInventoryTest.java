package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelInventoryTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final InventorySnapshot CHEST = InventorySnapshot.builder()
			.containerId(2)
			.stateId(5)
			.item(InventoryItem.builder().slot(4).protocolId(800).amount(3).build())
			.build();

	@Test
	void requestsSelectionsAndClicksFromTheWorker() {
		RecordingChannel channel = new RecordingChannel();
		ChannelInventory inventory = new ChannelInventory(channel);

		inventory.selectSlot(8);
		inventory.click(4, InventoryClick.HOTBAR_SWAP, 2);

		assertEquals(List.of("inventory.select", "inventory.click"), channel.requests);
		assertEquals(List.of(new SlotSelection(8), new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 2)), channel.payloads);
	}

	@Test
	void refusesAHotbarSlotOutsideTheHotbarWithoutRequesting() {
		RecordingChannel channel = new RecordingChannel();
		ChannelInventory inventory = new ChannelInventory(channel);

		assertThrows(IllegalArgumentException.class, () -> inventory.selectSlot(-1));
		assertThrows(IllegalArgumentException.class, () -> inventory.selectSlot(9));

		assertTrue(channel.requests.isEmpty());
	}

	@Test
	void startsEmptyAndKeepsTheLatestPublishedSnapshot() {
		RecordingChannel channel = new RecordingChannel();
		ChannelInventory inventory = new ChannelInventory(channel);
		assertEquals(InventorySnapshot.builder().containerId(0).stateId(0).build(), inventory.snapshot());

		channel.publish(InventoryOperations.CHANGED, CHEST);

		assertSame(CHEST, inventory.snapshot());
	}

	@Test
	void waitsForAMatchingSnapshot() {
		RecordingChannel channel = new RecordingChannel();
		ChannelInventory inventory = new ChannelInventory(channel);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> inventory.matching(snapshot -> snapshot.getContainerId() == 2, TIMEOUT));
		assertEquals("Player did not receive matching inventory", failure.getMessage());

		channel.publish(InventoryOperations.CHANGED, CHEST);
		assertSame(CHEST, inventory.matching(snapshot -> snapshot.getContainerId() == 2, TIMEOUT));
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
			return Set.of(InventoryProvider.ID);
		}
	}
}
