package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TakeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTrackerTest {
	private static final SimpleItemDefinition STONE = new SimpleItemDefinition("minecraft:stone", 1, false);
	private static final SimpleItemDefinition DIRT = new SimpleItemDefinition("minecraft:dirt", 2, false);

	private final InventoryTracker tracker = new InventoryTracker();

	@Test
	void followsThePlayersContainersAndOpenedContainersUntilTheyClose() {
		tracker.received(content(ContainerId.INVENTORY, List.of(stack(STONE, 3, 11), ItemData.AIR, stack(DIRT, 1, 12))));
		tracker.received(slot(ContainerId.OFFHAND, 0, stack(DIRT, 2, 13)));
		tracker.received(slot(5, 0, stack(STONE, 1, 14)));
		tracker.received(open(5));
		tracker.received(slot(5, 1, stack(STONE, 2, 15)));

		assertEquals(3, count(ContainerId.INVENTORY, 0));
		assertTrue(tracker.item(ContainerId.INVENTORY, 1).isEmpty());
		assertEquals(2, count(ContainerId.OFFHAND, 0));
		assertTrue(tracker.item(5, 0).isEmpty());
		assertEquals(2, count(5, 1));

		ContainerClosePacket close = new ContainerClosePacket();
		close.setId((byte) 5);
		tracker.received(close);
		assertTrue(tracker.item(5, 1).isEmpty());
	}

	@Test
	void selectsTheSlotTheServerOrTheClientItselfSelectedLast() {
		tracker.received(start(7, false));
		PlayerHotbarPacket hotbar = new PlayerHotbarPacket();
		hotbar.setSelectedHotbarSlot(4);
		hotbar.setSelectHotbarSlot(true);
		tracker.received(hotbar);
		assertEquals(4, tracker.selectedSlot());

		tracker.received(equipment(8, 6));
		assertEquals(4, tracker.selectedSlot());
		tracker.received(equipment(7, 6));
		assertEquals(6, tracker.selectedSlot());
		tracker.sent(equipment(7, 2));
		assertEquals(2, tracker.selectedSlot());
	}

	@Test
	void equipsTheSlotsItemOrTheEmptyItemAndRefusesSlotsOutsideTheHotbar() {
		assertThrows(IllegalStateException.class, () -> tracker.equip(0));
		tracker.received(start(7, false));
		ItemData stone = stack(STONE, 3, 11);
		tracker.received(slot(ContainerId.INVENTORY, 1, stone));

		MobEquipmentPacket held = tracker.equip(1);
		MobEquipmentPacket empty = tracker.equip(2);

		assertEquals(stone, held.getItem());
		assertEquals(7, held.getRuntimeEntityId());
		assertEquals(ContainerId.INVENTORY, held.getContainerId());
		assertEquals(ItemData.AIR, empty.getItem());
		assertThrows(IllegalArgumentException.class, () -> tracker.equip(9));
	}

	@Test
	void appliesTheSlotsItsOwnInventoryTransactionChanges() {
		ItemData stone = stack(STONE, 3, 11);
		tracker.received(slot(ContainerId.INVENTORY, 0, stone));
		InventoryTransactionPacket transaction = new InventoryTransactionPacket();
		transaction.setTransactionType(InventoryTransactionType.NORMAL);
		transaction.getActions().add(new InventoryActionData(InventorySource.fromContainerWindowId(ContainerId.INVENTORY), 0, stone, ItemData.AIR));
		transaction.getActions().add(new InventoryActionData(InventorySource.fromContainerWindowId(ContainerId.UI), 0, ItemData.AIR, stone));

		tracker.sent(transaction);

		assertTrue(tracker.item(ContainerId.INVENTORY, 0).isEmpty());
		assertEquals(3, count(ContainerId.UI, 0));
	}

	@Test
	void predictsItsItemStackRequestsUntilTheServerNamesTheStacks() {
		tracker.received(slot(ContainerId.INVENTORY, 0, stack(STONE, 3, 11)));
		tracker.sent(request(-1, new TakeAction(2, slotData(ContainerSlotType.HOTBAR, 0, 11), slotData(ContainerSlotType.CURSOR, 0, 0))));

		assertEquals(1, count(ContainerId.INVENTORY, 0));
		assertEquals(-1, tracker.item(ContainerId.INVENTORY, 0).orElseThrow().getNetId());
		assertEquals(2, count(ContainerId.UI, 0));
		assertEquals(-1, tracker.item(ContainerId.UI, 0).orElseThrow().getNetId());

		tracker.received(response(-1, ItemStackResponseStatus.OK,
				container(ContainerSlotType.HOTBAR, new ItemStackResponseSlot(0, 0, 1, 21, "", 0, "")),
				container(ContainerSlotType.CURSOR, new ItemStackResponseSlot(0, 0, 2, 22, "", 0, ""))));

		assertEquals(21, tracker.item(ContainerId.INVENTORY, 0).orElseThrow().getNetId());
		assertEquals(22, tracker.item(ContainerId.UI, 0).orElseThrow().getNetId());
	}

	@Test
	void restoresTheSlotsOfARejectedRequest() {
		ItemData stone = stack(STONE, 3, 11);
		ItemData dirt = stack(DIRT, 1, 12);
		tracker.received(slot(ContainerId.INVENTORY, 0, stone));
		tracker.received(slot(ContainerId.UI, 0, dirt));
		tracker.sent(request(-3,
				new SwapAction(slotData(ContainerSlotType.HOTBAR, 0, 11), slotData(ContainerSlotType.CURSOR, 0, 12)),
				new DropAction(1, slotData(ContainerSlotType.HOTBAR, 0, -3), false)));
		assertTrue(tracker.item(ContainerId.INVENTORY, 0).isEmpty());
		assertEquals(3, count(ContainerId.UI, 0));

		tracker.received(response(-3, ItemStackResponseStatus.ERROR));

		assertEquals(Optional.of(stone), tracker.item(ContainerId.INVENTORY, 0));
		assertEquals(Optional.of(dirt), tracker.item(ContainerId.UI, 0));
	}

	@Test
	void numbersItsItemStackRequestsNegativeAndOdd() {
		assertEquals(-1, tracker.nextRequestId());
		assertEquals(-3, tracker.nextRequestId());
		assertEquals(-5, tracker.nextRequestId());
	}

	@Test
	void knowsTheInventoryAuthorityOnlyAfterTheGameStart() {
		assertThrows(IllegalStateException.class, tracker::serverAuthoritative);
		tracker.received(start(7, true));
		assertTrue(tracker.serverAuthoritative());
		tracker.received(start(7, false));
		assertFalse(tracker.serverAuthoritative());
	}

	private int count(int containerId, int slot) {
		return tracker.item(containerId, slot).orElseThrow().getCount();
	}

	private static ItemData stack(SimpleItemDefinition definition, int count, int netId) {
		return ItemData.builder().definition(definition).count(count).usingNetId(true).netId(netId).build();
	}

	private static StartGamePacket start(long runtimeEntityId, boolean serverInventory) {
		StartGamePacket start = new StartGamePacket();
		start.setRuntimeEntityId(runtimeEntityId);
		start.setInventoriesServerAuthoritative(serverInventory);
		return start;
	}

	private static MobEquipmentPacket equipment(long runtimeEntityId, int slot) {
		MobEquipmentPacket packet = new MobEquipmentPacket();
		packet.setRuntimeEntityId(runtimeEntityId);
		packet.setHotbarSlot(slot);
		packet.setInventorySlot(slot);
		packet.setContainerId(ContainerId.INVENTORY);
		packet.setItem(ItemData.AIR);
		return packet;
	}

	private static ContainerOpenPacket open(int containerId) {
		ContainerOpenPacket open = new ContainerOpenPacket();
		open.setId((byte) containerId);
		open.setType(ContainerType.CONTAINER);
		return open;
	}

	private static InventoryContentPacket content(int containerId, List<ItemData> items) {
		InventoryContentPacket content = new InventoryContentPacket();
		content.setContainerId(containerId);
		content.setContents(new ArrayList<>(items));
		return content;
	}

	private static InventorySlotPacket slot(int containerId, int slot, ItemData item) {
		InventorySlotPacket packet = new InventorySlotPacket();
		packet.setContainerId(containerId);
		packet.setSlot(slot);
		packet.setItem(item);
		return packet;
	}

	private static ItemStackRequestSlotData slotData(ContainerSlotType type, int slot, int netId) {
		return new ItemStackRequestSlotData(type, slot, netId, new FullContainerName(type, null));
	}

	private static ItemStackRequestPacket request(int requestId, ItemStackRequestAction... actions) {
		ItemStackRequestPacket packet = new ItemStackRequestPacket();
		packet.getRequests().add(new ItemStackRequest(requestId, actions, new String[0]));
		return packet;
	}

	private static ItemStackResponseContainer container(ContainerSlotType type, ItemStackResponseSlot... slots) {
		return new ItemStackResponseContainer(type, List.of(slots), new FullContainerName(type, null));
	}

	private static ItemStackResponsePacket response(int requestId, ItemStackResponseStatus status, ItemStackResponseContainer... containers) {
		ItemStackResponsePacket packet = new ItemStackResponsePacket();
		packet.getEntries().add(new ItemStackResponse(status, requestId, List.of(containers)));
		return packet;
	}
}
