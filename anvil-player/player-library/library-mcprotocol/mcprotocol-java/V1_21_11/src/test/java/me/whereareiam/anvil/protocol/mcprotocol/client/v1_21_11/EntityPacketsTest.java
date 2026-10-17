package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
import me.whereareiam.anvil.protocol.java.api.model.entity.DisplayText;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityPosition;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundEntityPositionSync;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityPacketsTest {
	private static final UUID STAND = new UUID(0, 7);

	private final EntityPackets packets = new EntityPackets();

	@Test
	void decodesSpawnsWithTheNamespacedType() {
		assertEquals(ClientboundAddEntity.builder().entityId(7).uniqueId(STAND).type(EntityTypeKey.minecraft("armor_stand"))
						.position(new EntityPosition(1.5, 64, -2.5)).yaw(90).pitch(10).build(),
				packets.decode(new ClientboundAddEntityPacket(7, STAND, EntityType.ARMOR_STAND, 1.5, 64, -2.5, 90, 10, 45)));
		assertEquals(ClientboundAddEntity.builder().entityId(8).uniqueId(new UUID(0, 8))
						.type(EntityTypeKey.minecraft("text_display")).position(new EntityPosition(0, 66, 0)).build(),
				packets.decode(new ClientboundAddEntityPacket(8, new UUID(0, 8), EntityType.TEXT_DISPLAY, 0, 66, 0, 0, 0, 0)));
		assertEquals(ClientboundAddEntity.builder().entityId(9).uniqueId(new UUID(0, 9))
						.type(EntityTypeKey.minecraft("experience_orb")).position(new EntityPosition(1, 2, 3)).build(),
				packets.decode(new ClientboundAddEntityPacket(9, new UUID(0, 9), EntityType.EXPERIENCE_ORB, 1, 2, 3, 0, 0, 0)));
	}

	@Test
	void decodesTheValuesEveryEntityKeepsAndLeavesOutWhatItsTypeDoesNotKeep() {
		add(7, EntityType.ARMOR_STAND);

		assertEquals(new ClientboundSetEntityData(7, List.of(
				SharedFlags.builder().onFire(true).sneaking(true).invisible(true).glowing(true).build(),
				new CustomName("Alice: hello"),
				new CustomNameVisible(true)
		)), packets.decode(data(7,
				new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) 0x63),
				new ObjectEntityMetadata<>(2, MetadataTypes.OPTIONAL_COMPONENT,
						Optional.of(Component.text("Alice: ").append(Component.text("hello")))),
				new BooleanEntityMetadata(3, MetadataTypes.BOOLEAN, true),
				new BooleanEntityMetadata(5, MetadataTypes.BOOLEAN, true),
				new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, new ItemStack(1, 1)),
				new ObjectEntityMetadata<>(23, MetadataTypes.COMPONENT, Component.text("not a display"))
		)));
	}

	@Test
	void decodesTheItemOfADroppedItemWithEveryOtherFlagAndARemovedName() {
		add(8, EntityType.ITEM);

		assertEquals(new ClientboundSetEntityData(8, List.of(
				SharedFlags.builder().sprinting(true).swimming(true).gliding(true).build(),
				new CustomName(null),
				new CustomNameVisible(false),
				new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(871, 3))
		)), packets.decode(data(8,
				new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) 0x9C),
				new ObjectEntityMetadata<>(2, MetadataTypes.OPTIONAL_COMPONENT, Optional.empty()),
				new BooleanEntityMetadata(3, MetadataTypes.BOOLEAN, false),
				new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, new ItemStack(871, 3))
		)));
		assertEquals(new ClientboundSetEntityData(8, List.of(new ShownItem(null))),
				packets.decode(data(8, new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, null))));
	}

	@Test
	void decodesTheTextOfATextDisplayAndTheItemOfAnItemDisplay() {
		add(9, EntityType.TEXT_DISPLAY);
		add(10, EntityType.ITEM_DISPLAY);

		assertEquals(new ClientboundSetEntityData(9, List.of(new DisplayText("bubble"))), packets.decode(data(9,
				new ObjectEntityMetadata<>(23, MetadataTypes.COMPONENT, Component.text("bubble")))));
		assertEquals(new ClientboundSetEntityData(10, List.of(
				new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(40, 1))
		)), packets.decode(data(10,
				new ObjectEntityMetadata<>(23, MetadataTypes.ITEM_STACK, new ItemStack(40, 1)),
				new ObjectEntityMetadata<>(8, MetadataTypes.INT, 0)
		)));
	}

	@Test
	void decodesTheItemOfAnItemFrameAfterItsDirectionAndOfASplashPotion() {
		add(11, EntityType.GLOW_ITEM_FRAME);
		add(12, EntityType.SPLASH_POTION);

		assertEquals(List.of(new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(40, 1))),
				decodeData(data(11,
						new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, new ItemStack(1, 1)),
						new ObjectEntityMetadata<>(9, MetadataTypes.ITEM_STACK, new ItemStack(40, 1)))));
		assertEquals(List.of(new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(5, 1))),
				decodeData(data(12, new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, new ItemStack(5, 1)))));
	}

	@Test
	void decodesOnlyTheValuesEveryEntityKeepsForAnEntityTheSessionNeverAdded() {
		assertEquals(new ClientboundSetEntityData(42, List.of(SharedFlags.builder().sneaking(true).build())),
				packets.decode(data(42,
						new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) 0x02),
						new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, new ItemStack(871, 3))
				)));
	}

	@Test
	void leavesOutAValueOfAnotherKindThanTheTableNames() {
		add(9, EntityType.TEXT_DISPLAY);

		assertEquals(new ClientboundSetEntityData(9, List.of()), packets.decode(data(9,
				new ObjectEntityMetadata<>(23, MetadataTypes.ITEM_STACK, new ItemStack(40, 1)),
				new BooleanEntityMetadata(0, MetadataTypes.BOOLEAN, true)
		)));
	}

	@Test
	void forgetsTheTypeOfARemovedEntityAndOfEveryEntityOfALevelThePlayerLeaves() {
		add(7, EntityType.ITEM);
		add(8, EntityType.ITEM);
		add(9, EntityType.ITEM);
		ClientboundSetEntityDataPacket item7 = data(7, new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, null));

		assertEquals(new ClientboundRemoveEntities(List.of(7)), packets.decode(new ClientboundRemoveEntitiesPacket(new int[]{7})));
		assertEquals(new ClientboundSetEntityData(7, List.of()), packets.decode(item7));
		assertEquals(List.of(new ShownItem(null)), decodeData(data(8, new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, null))));

		assertNull(packets.decode(new ClientboundRespawnPacket(null, false, false)));
		assertEquals(List.of(), decodeData(data(8, new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, null))));

		add(9, EntityType.ITEM);
		assertNull(packets.decode(new ClientboundStartConfigurationPacket()));
		assertEquals(List.of(), decodeData(data(9, new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, null))));

		add(9, EntityType.ITEM);
		assertNull(packets.decode(new ClientboundLoginPacket(42, false, new Key[0], 20, 10, 10, false, true, false, null,
				false)));
		assertEquals(List.of(), decodeData(data(9, new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, null))));
	}

	@Test
	void decodesMovesTurnsTeleportsAndPositionSyncs() {
		assertEquals(ClientboundMoveEntityPos.builder().entityId(7).deltaX(0.5).deltaY(-1).deltaZ(0.25).build(),
				packets.decode(new ClientboundMoveEntityPosPacket(7, 0.5, -1, 0.25, true)));
		assertEquals(ClientboundMoveEntityPosRot.builder().entityId(7).deltaX(1).yaw(90).pitch(10).build(),
				packets.decode(new ClientboundMoveEntityPosRotPacket(7, 1, 0, 0, 90, 10, true)));
		assertEquals(new ClientboundMoveEntityRot(7, 180, -10),
				packets.decode(new ClientboundMoveEntityRotPacket(7, 180, -10, true)));
		assertEquals(ClientboundTeleportEntity.builder().entityId(7).position(new EntityPosition(1, 2, 3)).yaw(15).pitch(5)
						.relatives(Set.of(Relative.Y, Relative.YAW)).build(),
				packets.decode(new ClientboundTeleportEntityPacket(7, Vector3d.from(1, 2, 3), Vector3d.ZERO, 15, 5,
						List.of(PositionElement.Y, PositionElement.Y_ROT, PositionElement.DELTA_X), true)));
		assertEquals(ClientboundEntityPositionSync.builder().entityId(7).position(new EntityPosition(4, 5, 6)).yaw(30)
						.pitch(20).build(),
				packets.decode(new ClientboundEntityPositionSyncPacket(7, Vector3d.from(4, 5, 6), Vector3d.ZERO, 30, 20,
						true)));
	}

	@Test
	void decodesPassengersAndRemovals() {
		assertEquals(new ClientboundSetPassengers(42, List.of(7, 8)),
				packets.decode(new ClientboundSetPassengersPacket(42, new int[]{7, 8})));
		assertEquals(new ClientboundRemoveEntities(List.of(7, 8)),
				packets.decode(new ClientboundRemoveEntitiesPacket(new int[]{7, 8})));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryEntityPacketOfTheRelease() {
		assertEquals(Set.of(
				ClientboundAddEntity.class,
				ClientboundMoveEntityPos.class,
				ClientboundMoveEntityPosRot.class,
				ClientboundMoveEntityRot.class,
				ClientboundTeleportEntity.class,
				ClientboundEntityPositionSync.class,
				ClientboundSetEntityData.class,
				ClientboundSetPassengers.class,
				ClientboundRemoveEntities.class
		), packets.packets());
	}

	private void add(int entityId, EntityType type) {
		packets.decode(new ClientboundAddEntityPacket(entityId, new UUID(0, entityId), type, 0, 0, 0, 0, 0, 0));
	}

	private List<?> decodeData(ClientboundSetEntityDataPacket packet) {
		return ((ClientboundSetEntityData) packets.decode(packet)).getValues();
	}

	private static ClientboundSetEntityDataPacket data(int entityId, EntityMetadata<?, ?>... metadata) {
		return new ClientboundSetEntityDataPacket(entityId, metadata);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
