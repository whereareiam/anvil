package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.inventory.channel.InventoryChannel;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.ItemType;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelInventoryTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final InventorySnapshot CHEST = InventorySnapshot.builder()
			.containerId(2)
			.stateId(5)
			.item(InventoryItem.builder().slot(4).type(new ItemType("minecraft:diamond", 800)).amount(3).build())
			.build();

	@Test
	void requestsSelectionsAndClicksFromTheWorker() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(InventoryChannel.ID));
		channel.answer(InventoryChannel.SELECT, selection -> null);
		channel.answer(InventoryChannel.CLICK, action -> null);
		ChannelInventory inventory = new ChannelInventory(channel);

		inventory.selectSlot(8);
		inventory.click(4, InventoryClick.HOTBAR_SWAP, 2);

		assertEquals(List.of("inventory.select", "inventory.click"), channel.requests());
		assertEquals(List.of(new SlotSelection(8), new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 2)), channel.payloads());
	}

	@Test
	void refusesAHotbarSlotOutsideTheHotbarWithoutRequesting() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(InventoryChannel.ID));
		ChannelInventory inventory = new ChannelInventory(channel);

		assertThrows(IllegalArgumentException.class, () -> inventory.selectSlot(-1));
		assertThrows(IllegalArgumentException.class, () -> inventory.selectSlot(9));

		assertTrue(channel.requests().isEmpty());
	}

	@Test
	void startsEmptyAndKeepsTheLatestPublishedSnapshot() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(InventoryChannel.ID));
		ChannelInventory inventory = new ChannelInventory(channel);
		assertEquals(InventorySnapshot.builder().containerId(0).stateId(0).build(), inventory.snapshot());

		channel.publish(InventoryChannel.CHANGED, CHEST);

		assertSame(CHEST, inventory.snapshot());
	}

	@Test
	void waitsForAMatchingSnapshot() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(InventoryChannel.ID));
		ChannelInventory inventory = new ChannelInventory(channel);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> inventory.matching(snapshot -> snapshot.getContainerId() == 2, TIMEOUT));
		assertEquals("Player did not receive matching inventory", failure.getMessage());

		channel.publish(InventoryChannel.CHANGED, CHEST);
		assertSame(CHEST, inventory.matching(snapshot -> snapshot.getContainerId() == 2, TIMEOUT));
	}
}
