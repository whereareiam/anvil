package me.whereareiam.anvil.capability.interaction.cloudburst.v1_26_60;

import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientPosition;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.jetbrains.annotations.NotNull;
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

class CloudburstInteractionPacketsTest {
	private static final ItemDefinition SWORD = new SimpleItemDefinition("minecraft:iron_sword", 307, false);
	private static final ItemDefinition SHIELD = new SimpleItemDefinition("minecraft:shield", 355, false);

	private final CloudburstInteractionPackets packets = new CloudburstInteractionPackets();
	private final RecordingSession session = new RecordingSession();

	@Test
	void castsTheNativeSessionThroughTheSessionInterface() {
		assertSame(session, packets.sessionType().cast(session));
	}

	@Test
	void swingsTheArmOfThePlayersOwnEntity() {
		session.protocol = 2225;

		packets.swing(session, Hand.MAIN);
		packets.swing(session, Hand.OFF);

		AnimatePacket main = (AnimatePacket) session.sent.get(0);
		assertEquals(AnimatePacket.Action.SWING_ARM, main.getAction());
		assertEquals(7, main.getRuntimeEntityId());
		assertEquals(HandSlot.MAINHAND, main.getHand());
		assertEquals(HandSlot.OFFHAND, ((AnimatePacket) session.sent.get(1)).getHand());
	}

	@Test
	void usesTheSelectedSlotsItemInTheAirFromThePlayersEyes() {
		ItemData sword = ItemData.builder().definition(SWORD).count(1).build();
		session.items.put(ContainerId.INVENTORY + ":4", sword);
		session.selected = 4;

		packets.useItem(session, Hand.MAIN, 9, 90, 10);

		InventoryTransactionPacket use = (InventoryTransactionPacket) session.sent.getFirst();
		assertEquals(InventoryTransactionType.ITEM_USE, use.getTransactionType());
		assertEquals(1, use.getActionType());
		assertEquals(ItemUseTransaction.TriggerType.PLAYER_INPUT, use.getTriggerType());
		assertEquals(4, use.getHotbarSlot());
		assertSame(sword, use.getItemInHand());
		assertEquals(HandSlot.MAINHAND, use.getHand());
		assertEquals(Vector3f.from(1, 2.62F, 3), use.getPlayerPosition());
		assertEquals(Vector3i.ZERO, use.getBlockPosition());
		assertEquals(-1, use.getBlockFace());
		assertEquals(0, use.getBlockDefinition().getRuntimeId());
		assertEquals(ItemUseTransaction.PredictedResult.FAILURE, use.getClientInteractPrediction());
	}

	@Test
	void usesTheOffHandsItemWhenTheProtocolCarriesTheHand() {
		ItemData shield = ItemData.builder().definition(SHIELD).count(1).build();
		session.items.put(ContainerId.OFFHAND + ":0", shield);

		packets.useItem(session, Hand.OFF, 1, 0, 0);

		InventoryTransactionPacket use = (InventoryTransactionPacket) session.sent.getFirst();
		assertEquals(HandSlot.OFFHAND, use.getHand());
		assertSame(shield, use.getItemInHand());
	}

	@Test
	void usesAnItemOnTheCenterOfAKnownBlocksFace() {
		session.blocks.put("10 64 -3", 1234);

		packets.useItemOn(session, BlockPosition.builder().x(10).y(64).z(-3).build(), BlockFace.UP, Hand.MAIN, 5);

		InventoryTransactionPacket use = (InventoryTransactionPacket) session.sent.getFirst();
		assertEquals(0, use.getActionType());
		assertEquals(Vector3i.from(10, 64, -3), use.getBlockPosition());
		assertEquals(1, use.getBlockFace());
		assertEquals(Vector3f.from(0.5F, 1, 0.5F), use.getClickPosition());
		assertEquals(1234, use.getBlockDefinition().getRuntimeId());
		assertEquals(ItemData.AIR, use.getItemInHand());
		assertEquals(ItemUseTransaction.PredictedResult.SUCCESS, use.getClientInteractPrediction());
	}

	@Test
	void refusesToUseAnItemOnABlockTheSessionHasNotReceived() {
		IllegalStateException refused = assertThrows(IllegalStateException.class, () ->
				packets.useItemOn(session, BlockPosition.builder().x(1).y(2).z(3).build(), BlockFace.NORTH, Hand.MAIN, 1));

		assertTrue(refused.getMessage().contains("has not received the block at 1 2 3"), refused.getMessage());
		assertTrue(session.sent.isEmpty());
	}

	@Test
	void interactsWithAndAttacksAnEntity() {
		packets.entity(session, 42, EntityInteraction.INTERACT, Hand.MAIN);
		packets.entity(session, 43, EntityInteraction.ATTACK, Hand.MAIN);

		InventoryTransactionPacket interact = (InventoryTransactionPacket) session.sent.get(0);
		InventoryTransactionPacket attack = (InventoryTransactionPacket) session.sent.get(1);
		assertEquals(InventoryTransactionType.ITEM_USE_ON_ENTITY, interact.getTransactionType());
		assertEquals(42, interact.getRuntimeEntityId());
		assertEquals(0, interact.getActionType());
		assertEquals(43, attack.getRuntimeEntityId());
		assertEquals(1, attack.getActionType());
		assertEquals(Vector3f.from(1, 2.62F, 3), attack.getPlayerPosition());
		assertEquals(Vector3f.ZERO, attack.getClickPosition());
	}

	@Test
	void refusesTheOffHandWhereThePlayersProtocolActsWithTheMainHandOnly() {
		session.protocol = 2169;

		UnsupportedOperationException swing = assertThrows(UnsupportedOperationException.class, () -> packets.swing(session, Hand.OFF));
		UnsupportedOperationException use = assertThrows(UnsupportedOperationException.class, () -> packets.useItem(session, Hand.OFF, 1, 0, 0));
		UnsupportedOperationException entity = assertThrows(UnsupportedOperationException.class, () ->
				packets.entity(session, 1, EntityInteraction.ATTACK, Hand.OFF));

		assertTrue(swing.getMessage().contains("protocol 2225"), swing.getMessage());
		assertTrue(use.getMessage().contains("protocol 2193"), use.getMessage());
		assertTrue(entity.getMessage().contains("protocol 2225"), entity.getMessage());
		assertTrue(session.sent.isEmpty());
	}

	/**
	 * A started session that records what is sent and knows the items, blocks and position a test gives it.
	 */
	private static final class RecordingSession implements CloudburstSession<BedrockPacket, ItemDefinition, ItemData> {
		private final List<BedrockPacket> sent = new ArrayList<>();
		private final Map<String, ItemData> items = new HashMap<>();
		private final Map<String, Integer> blocks = new HashMap<>();
		private int protocol = 2193;
		private int selected;

		@Override
		public int protocol() {
			return protocol;
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
			throw new AssertionError("Interaction subscribes to nothing");
		}

		@Override
		public @NotNull Optional<GameStart> start() {
			return Optional.of(GameStart.builder().runtimeEntityId(7).uniqueEntityId(70).build());
		}

		@Override
		public boolean serverAuthoritativeMovement() {
			return true;
		}

		@Override
		public @NotNull List<ItemDefinition> itemDefinitions() {
			return List.of(SWORD, SHIELD);
		}

		@Override
		public long tick() {
			return 0;
		}

		@Override
		public boolean serverAuthoritativeInventory() {
			return false;
		}

		@Override
		public int nextItemStackRequestId() {
			throw new AssertionError("Interaction sends no item stack request");
		}

		@Override
		public int selectedSlot() {
			return selected;
		}

		@Override
		public @NotNull Optional<ItemData> item(int containerId, int slot) {
			return Optional.ofNullable(items.get(containerId + ":" + slot));
		}

		@Override
		public void select(int slot) {
			throw new AssertionError("Interaction selects no slot");
		}

		@Override
		public @NotNull ClientPosition position() {
			return ClientPosition.builder().x(1).y(2.62F).z(3).build();
		}

		@Override
		public @NotNull OptionalInt blockAt(int x, int y, int z) {
			Integer block = blocks.get(x + " " + y + " " + z);
			return block == null ? OptionalInt.empty() : OptionalInt.of(block);
		}
	}
}
