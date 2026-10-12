package me.whereareiam.anvil.capability.inventory.cloudburst.v1_26_60;

import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
	void refusesToSelectOrClickWithoutTheClientsItemStacks() {
		UnsupportedOperationException select = assertThrows(UnsupportedOperationException.class, () -> packets.selectHotbar(session, 2));
		UnsupportedOperationException click = assertThrows(UnsupportedOperationException.class, () -> packets.click(session, ContainerClick.builder()
				.containerId(0)
				.slot(9)
				.click(InventoryClick.LEFT)
				.build()));

		assertTrue(select.getMessage().contains("mob equipment"), select.getMessage());
		assertTrue(click.getMessage().contains("item stack requests"), click.getMessage());
		assertTrue(session.sent.isEmpty());
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
	private static final class Session implements CloudburstSession<BedrockPacket, ItemDefinition> {
		private final List<Listener<?>> subscriptions = new ArrayList<>();
		private final List<BedrockPacket> sent = new ArrayList<>();

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
