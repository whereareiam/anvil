package me.whereareiam.anvil.capability.inventory.cloudburst.v1_26_60;

import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientPosition;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.PlaceAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TakeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudburstInventoryPacketsTest {
	private static final ItemDefinition STONE = new SimpleItemDefinition("minecraft:stone", 1, false);
	private static final ItemDefinition HELMET = new SimpleItemDefinition("minecraft:iron_helmet", 306, false);

	private final CloudburstInventoryPackets packets = new CloudburstInventoryPackets();
	private final Session session = new Session();
	private final Recorder recorder = new Recorder();

	@Test
	void castsTheNativeSessionThroughTheSessionInterface() {
		assertSame(session, packets.sessionType().cast(session));
	}

	@Test
	void reportsTheSlotsOfThePlayersOwnContainersByInventoryIndex() {
		packets.listen(session, recorder);

		session.receive(content(ContainerId.INVENTORY, ItemData.AIR, stack(STONE, 3)));
		session.receive(content(ContainerId.ARMOR, stack(HELMET, 1), ItemData.AIR, ItemData.AIR, ItemData.AIR));
		session.receive(slot(ContainerId.OFFHAND, 0, stack(STONE, 2)));
		session.receive(slot(ContainerId.INVENTORY, 36, stack(STONE, 1)));

		assertEquals(List.of(
				"player 0 empty",
				"player 1 minecraft:stone#1x3@1",
				"player 39 minecraft:iron_helmet#306x1@39",
				"player 38 empty",
				"player 37 empty",
				"player 36 empty",
				"player 40 minecraft:stone#1x2@40"
		), recorder.events);
	}

	@Test
	void reportsOpenedContainersWithTheSlotsTheyArriveWith() {
		packets.listen(session, recorder);

		session.receive(open(ContainerId.INVENTORY));
		session.receive(open(7));
		session.receive(content(7, ItemData.AIR, ItemData.AIR, stack(STONE, 64)));
		session.receive(slot(7, 0, stack(STONE, 1)));
		session.receive(slot(7, 2, ItemData.AIR));

		assertEquals(List.of(
				"opened 7",
				"contents 7 state 0 [minecraft:stone#1x64@2]",
				"slot 7 state 0 slot 0 minecraft:stone#1x1@0",
				"slot 7 state 0 slot 2 empty"
		), recorder.events);
	}

	@Test
	void leavesTheCursorAndOtherUserInterfaceContainersOut() {
		packets.listen(session, recorder);

		session.receive(content(ContainerId.UI, stack(STONE, 1)));
		session.receive(slot(ContainerId.UI, 0, stack(STONE, 1)));
		session.receive(slot(ContainerId.CONTAINER_ID_REGISTRY, 0, stack(STONE, 1)));

		assertTrue(recorder.events.isEmpty(), recorder.events.toString());
	}

	@Test
	void stopsReportingOnceDetached() {
		Runnable detach = packets.listen(session, recorder);

		detach.run();
		session.receive(open(7));

		assertTrue(recorder.events.isEmpty(), recorder.events.toString());
		assertTrue(session.subscriptions.isEmpty());
	}

	@Test
	void selectsAHotbarSlotThroughTheSession() {
		packets.selectHotbar(session, 4);

		assertEquals(List.of(4), session.selected);
	}

	@Test
	void picksAStackUpAsATransactionOfTheSlotAndTheCursorWithoutServerInventoryAuthority() {
		session.items.put(key(ContainerId.INVENTORY, 2), stack(STONE, 5));

		packets.click(session, click(38, InventoryClick.LEFT, 0));

		InventoryTransactionPacket transaction = (InventoryTransactionPacket) session.sent.getFirst();
		assertEquals(InventoryTransactionType.NORMAL, transaction.getTransactionType());
		assertEquals(List.of(
				"CONTAINER 0:2 minecraft:stone x5 -> air",
				"CONTAINER 124:0 air -> minecraft:stone x5"
		), describe(transaction));
	}

	@Test
	void takesHalfRoundedUpAndPlacesOneWithTheSecondaryButton() {
		session.items.put(key(ContainerId.INVENTORY, 12), stack(STONE, 5));
		packets.click(session, click(12, InventoryClick.RIGHT, 0));
		session.items.clear();
		session.items.put(key(ContainerId.UI, 0), stack(STONE, 5));
		packets.click(session, click(45, InventoryClick.RIGHT, 0));

		assertEquals(List.of(
				"CONTAINER 0:12 minecraft:stone x5 -> minecraft:stone x2",
				"CONTAINER 124:0 air -> minecraft:stone x3"
		), describe((InventoryTransactionPacket) session.sent.get(0)));
		assertEquals(List.of(
				"CONTAINER 124:0 minecraft:stone x5 -> minecraft:stone x4",
				"CONTAINER 119:0 air -> minecraft:stone x1"
		), describe((InventoryTransactionPacket) session.sent.get(1)));
	}

	@Test
	void swapsTheCursorWithADifferentItemAndDropsFromASlotIntoTheWorld() {
		session.items.put(key(ContainerId.UI, 0), stack(STONE, 2));
		session.items.put(key(ContainerId.ARMOR, 0), stack(HELMET, 1));
		session.items.put(key(7, 3), stack(STONE, 4));

		packets.click(session, click(5, InventoryClick.LEFT, 0));
		packets.click(session, ContainerClick.builder().containerId(7).slot(3).click(InventoryClick.DROP_ONE).build());

		assertEquals(List.of(
				"CONTAINER 124:0 minecraft:stone x2 -> minecraft:iron_helmet x1",
				"CONTAINER 120:0 minecraft:iron_helmet x1 -> minecraft:stone x2"
		), describe((InventoryTransactionPacket) session.sent.get(0)));
		assertEquals(List.of(
				"CONTAINER 7:3 minecraft:stone x4 -> minecraft:stone x3",
				"WORLD_INTERACTION -1:0 air -> minecraft:stone x1"
		), describe((InventoryTransactionPacket) session.sent.get(1)));
	}

	@Test
	void swapsWithAHotbarSlotOrMovesIntoTheEmptyOffHand() {
		session.items.put(key(ContainerId.INVENTORY, 20), stack(STONE, 3));
		session.items.put(key(ContainerId.INVENTORY, 1), stack(HELMET, 1));

		packets.click(session, click(20, InventoryClick.HOTBAR_SWAP, 1));
		packets.click(session, click(20, InventoryClick.HOTBAR_SWAP, ContainerClick.OFF_HAND_BUTTON));

		assertEquals(List.of(
				"CONTAINER 0:20 minecraft:stone x3 -> minecraft:iron_helmet x1",
				"CONTAINER 0:1 minecraft:iron_helmet x1 -> minecraft:stone x3"
		), describe((InventoryTransactionPacket) session.sent.get(0)));
		assertEquals(List.of(
				"CONTAINER 0:20 minecraft:stone x3 -> air",
				"CONTAINER 119:0 air -> minecraft:stone x3"
		), describe((InventoryTransactionPacket) session.sent.get(1)));
	}

	@Test
	void sendsNothingForAClickThatChangesNothing() {
		packets.click(session, click(9, InventoryClick.LEFT, 0));
		packets.click(session, click(9, InventoryClick.DROP_STACK, 0));
		packets.click(session, click(36, InventoryClick.HOTBAR_SWAP, 0));

		assertTrue(session.sent.isEmpty());
	}

	@Test
	void sendsItemStackRequestsThatNameEachStackUnderServerInventoryAuthority() {
		session.serverInventory = true;
		session.items.put(key(ContainerId.INVENTORY, 0), netStack(STONE, 5, 41));
		session.items.put(key(ContainerId.UI, 0), netStack(HELMET, 1, 42));

		packets.click(session, click(36, InventoryClick.LEFT, 0));
		session.items.remove(key(ContainerId.UI, 0));
		packets.click(session, click(36, InventoryClick.DROP_STACK, 0));

		ItemStackRequest swap = ((ItemStackRequestPacket) session.sent.get(0)).getRequests().getFirst();
		assertEquals(-1, swap.getRequestId());
		SwapAction action = (SwapAction) swap.getActions()[0];
		assertSlot(ContainerSlotType.CURSOR, 0, 42, action.getSource());
		assertSlot(ContainerSlotType.HOTBAR, 0, 41, action.getDestination());
		ItemStackRequest drop = ((ItemStackRequestPacket) session.sent.get(1)).getRequests().getFirst();
		assertEquals(-3, drop.getRequestId());
		DropAction dropped = (DropAction) drop.getActions()[0];
		assertEquals(5, dropped.getCount());
		assertSlot(ContainerSlotType.HOTBAR, 0, 41, dropped.getSource());
	}

	@Test
	void takesToTheCursorAndPlacesIntoTheInventoryWithItemStackRequests() {
		session.serverInventory = true;
		session.items.put(key(ContainerId.INVENTORY, 15), netStack(STONE, 4, 7));

		packets.click(session, click(15, InventoryClick.RIGHT, 0));
		session.items.clear();
		session.items.put(key(ContainerId.UI, 0), netStack(STONE, 4, 8));
		packets.click(session, click(16, InventoryClick.LEFT, 0));

		TakeAction take = (TakeAction) ((ItemStackRequestPacket) session.sent.get(0)).getRequests().getFirst().getActions()[0];
		assertEquals(2, take.getCount());
		assertSlot(ContainerSlotType.INVENTORY, 15, 7, take.getSource());
		assertSlot(ContainerSlotType.CURSOR, 0, 0, take.getDestination());
		PlaceAction place = (PlaceAction) ((ItemStackRequestPacket) session.sent.get(1)).getRequests().getFirst().getActions()[0];
		assertEquals(4, place.getCount());
		assertSlot(ContainerSlotType.CURSOR, 0, 8, place.getSource());
		assertSlot(ContainerSlotType.INVENTORY, 16, 0, place.getDestination());
	}

	@Test
	void refusesClicksItCannotSendFaithfully() {
		session.items.put(key(ContainerId.INVENTORY, 9), stack(STONE, 3));
		session.items.put(key(ContainerId.UI, 0), stack(STONE, 1));

		UnsupportedOperationException shift = assertThrows(UnsupportedOperationException.class, () -> packets.click(session, click(9, InventoryClick.SHIFT_LEFT, 0)));
		UnsupportedOperationException merge = assertThrows(UnsupportedOperationException.class, () -> packets.click(session, click(9, InventoryClick.LEFT, 0)));
		IllegalArgumentException crafting = assertThrows(IllegalArgumentException.class, () -> packets.click(session, click(1, InventoryClick.LEFT, 0)));
		session.serverInventory = true;
		UnsupportedOperationException container = assertThrows(UnsupportedOperationException.class, () ->
				packets.click(session, ContainerClick.builder().containerId(7).slot(0).click(InventoryClick.LEFT).build()));

		assertTrue(shift.getMessage().contains("shift-clicked"), shift.getMessage());
		assertTrue(merge.getMessage().contains("maximum stack size"), merge.getMessage());
		assertTrue(crafting.getMessage().contains("crafting"), crafting.getMessage());
		assertTrue(container.getMessage().contains("slot type"), container.getMessage());
		assertTrue(session.sent.isEmpty());
	}

	private static ContainerClick click(int slot, InventoryClick click, int button) {
		return ContainerClick.builder()
				.containerId(ContainerId.INVENTORY)
				.slot(slot)
				.click(click)
				.button(button)
				.build();
	}

	private static ItemData netStack(ItemDefinition definition, int count, int netId) {
		return stack(definition, count).toBuilder().usingNetId(true).netId(netId).build();
	}

	@SuppressWarnings("deprecation")
	private static void assertSlot(ContainerSlotType type, int slot, int netId, ItemStackRequestSlotData data) {
		assertEquals(type, data.getContainer());
		assertEquals(type, data.getContainerName().getContainer());
		assertEquals(slot, data.getSlot());
		assertEquals(netId, data.getStackNetworkId());
	}

	private static List<String> describe(InventoryTransactionPacket transaction) {
		return transaction.getActions().stream()
				.map(action -> action.getSource().getType() + " " + action.getSource().getContainerId() + ":" + action.getSlot() + " "
						+ describe(action.getFromItem()) + " -> " + describe(action.getToItem()))
				.toList();
	}

	private static String describe(ItemData stack) {
		return stack.isValid() ? stack.getDefinition().getIdentifier() + " x" + stack.getCount() : "air";
	}

	private static String key(int containerId, int slot) {
		return containerId + ":" + slot;
	}

	private static ItemData stack(ItemDefinition definition, int count) {
		return ItemData.builder().definition(definition).count(count).build();
	}

	private static InventoryContentPacket content(int containerId, ItemData... contents) {
		InventoryContentPacket packet = new InventoryContentPacket();
		packet.setContainerId(containerId);
		packet.setContents(List.of(contents));
		return packet;
	}

	private static InventorySlotPacket slot(int containerId, int slot, ItemData item) {
		InventorySlotPacket packet = new InventorySlotPacket();
		packet.setContainerId(containerId);
		packet.setSlot(slot);
		packet.setItem(item);
		return packet;
	}

	private static ContainerOpenPacket open(int containerId) {
		ContainerOpenPacket packet = new ContainerOpenPacket();
		packet.setId((byte) containerId);
		packet.setType(ContainerType.CONTAINER);
		return packet;
	}

	private static String describe(@Nullable InventoryItem item) {
		if (item == null) return "empty";

		return item.getIdentifier() + "#" + item.getProtocolId() + "x" + item.getAmount() + "@" + item.getSlot();
	}

	private static final class Recorder implements InventoryPacketListener {
		private final List<String> events = new ArrayList<>();

		@Override
		public void opened(int containerId) {
			events.add("opened " + containerId);
		}

		@Override
		public void contents(int containerId, int stateId, @NotNull List<InventoryItem> items) {
			events.add("contents " + containerId + " state " + stateId + " " + items.stream().map(CloudburstInventoryPacketsTest::describe).toList());
		}

		@Override
		public void slot(int containerId, int stateId, int slot, @Nullable InventoryItem item) {
			events.add("slot " + containerId + " state " + stateId + " slot " + slot + " " + describe(item));
		}

		@Override
		public void playerSlot(int slot, @Nullable InventoryItem item) {
			events.add("player " + slot + " " + describe(item));
		}
	}

	/**
	 * A connected session that delivers the packets a test hands it to its subscriptions.
	 */
	private static final class Session implements CloudburstSession<BedrockPacket, ItemDefinition, ItemData> {
		private final List<Listener<?>> subscriptions = new ArrayList<>();
		private final List<BedrockPacket> sent = new ArrayList<>();
		private final Map<String, ItemData> items = new HashMap<>();
		private final List<Integer> selected = new ArrayList<>();
		private boolean serverInventory;
		private int requestId = 1;

		@Override
		public int protocol() {
			return 2225;
		}

		@Override
		public boolean connected() {
			return true;
		}

		@Override
		public @NotNull ClientProfile profile() {
			return ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build();
		}

		@Override
		public void send(@NotNull BedrockPacket packet) {
			sent.add(packet);
		}

		@Override
		public <T extends BedrockPacket> @NotNull PacketSubscription subscribe(@NotNull Class<T> type, @NotNull Consumer<? super T> listener) {
			Listener<T> subscription = new Listener<>(type, listener);
			subscriptions.add(subscription);
			return () -> subscriptions.remove(subscription);
		}

		@Override
		public @NotNull Optional<GameStart> start() {
			return Optional.empty();
		}

		@Override
		public boolean serverAuthoritativeMovement() {
			throw new IllegalStateException("not started");
		}

		@Override
		public @NotNull List<ItemDefinition> itemDefinitions() {
			return List.of(STONE, HELMET);
		}

		@Override
		public long tick() {
			throw new IllegalStateException("not started");
		}

		@Override
		public boolean serverAuthoritativeInventory() {
			return serverInventory;
		}

		@Override
		public int nextItemStackRequestId() {
			requestId -= 2;
			return requestId;
		}

		@Override
		public int selectedSlot() {
			return selected.isEmpty() ? 0 : selected.getLast();
		}

		@Override
		public @NotNull Optional<ItemData> item(int containerId, int slot) {
			return Optional.ofNullable(items.get(key(containerId, slot)));
		}

		@Override
		public void select(int slot) {
			selected.add(slot);
		}

		@Override
		public @NotNull ClientPosition position() {
			throw new IllegalStateException("not started");
		}

		@Override
		public @NotNull OptionalInt blockAt(int x, int y, int z) {
			return OptionalInt.empty();
		}

		private void receive(BedrockPacket packet) {
			List.copyOf(subscriptions).forEach(subscription -> subscription.deliver(packet));
		}

		private record Listener<T extends BedrockPacket>(Class<T> type, Consumer<? super T> listener) {
			private void deliver(BedrockPacket packet) {
				if (type.isInstance(packet)) listener.accept(type.cast(packet));
			}
		}
	}
}
