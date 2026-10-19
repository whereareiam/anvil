package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.Animation;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.data.game.entity.type.PaintingType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundAnimatePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddMobPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPaintingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPlayerPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSwingAnimation;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import net.kyori.adventure.text.Component;
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
	void decodesEachSpawnPacketAsAnAddedEntityWithTheNamespacedType() {
		assertEquals(ClientboundAddEntity.builder().entityId(7).uniqueId(STAND)
				.type(EntityTypeKey.minecraft("armor_stand"))
						.position(new Vec3d(1.5, 64, -2.5)).yaw(90).pitch(10).build(),
				packets.decode(new ClientboundAddEntityPacket(7, STAND, EntityType.ARMOR_STAND, 1.5, 64, -2.5, 90,
						10)));
		assertEquals(ClientboundAddEntity.builder().entityId(8).uniqueId(new UUID(0, 8))
				.type(EntityTypeKey.minecraft("zombie"))
						.position(new Vec3d(0, 66, 0)).yaw(180).pitch(20).build(),
				packets.decode(new ClientboundAddMobPacket(8, new UUID(0, 8), EntityType.ZOMBIE, 0, 66, 0, 20, 180, 45,
						0, 0,
						0)));
		assertEquals(ClientboundAddEntity.builder().entityId(9).uniqueId(new UUID(0, 9))
						.type(EntityTypeKey.minecraft("player")).position(new Vec3d(2, 64, 2)).yaw(270).pitch(-30)
								.build(),
				packets.decode(new ClientboundAddPlayerPacket(9, new UUID(0, 9), 2, 64, 2, 270, -30)));
		assertEquals(ClientboundAddEntity.builder().entityId(10).uniqueId(new UUID(0, 10))
						.type(EntityTypeKey.minecraft("painting")).position(new Vec3d(4, 65, 4)).build(),
				packets.decode(new ClientboundAddPaintingPacket(10, new UUID(0, 10), PaintingType.KEBAB,
						new Position(4, 65, 4), Direction.NORTH)));
		assertEquals(ClientboundAddEntity.builder().entityId(11).type(EntityTypeKey.minecraft("experience_orb"))
						.position(new Vec3d(1, 2, 3)).build(),
				packets.decode(new ClientboundAddExperienceOrbPacket(11, 1, 2, 3, 5)));
	}

	@Test
	void decodesTheValuesEveryEntityKeepsAndLeavesOutWhatItsTypeDoesNotKeep() {
		add(7, EntityType.ARMOR_STAND);

		assertEquals(new ClientboundSetEntityData(7, List.of(
				SharedFlags.builder().onFire(true).sneaking(true).invisible(true).glowing(true).build(),
				new CustomName("Alice: hello"),
				new CustomNameVisible(true)
		)), packets.decode(data(7,
				new ByteEntityMetadata(0, MetadataType.BYTE, (byte) 0x63),
				new ObjectEntityMetadata<>(2, MetadataType.OPTIONAL_CHAT,
						Optional.of(Component.text("Alice: ").append(Component.text("hello")))),
				new BooleanEntityMetadata(3, MetadataType.BOOLEAN, true),
				new BooleanEntityMetadata(5, MetadataType.BOOLEAN, true),
				new ObjectEntityMetadata<>(8, MetadataType.ITEM, new ItemStack(1, 1)),
				new ObjectEntityMetadata<>(23, MetadataType.CHAT, Component.text("not a display"))
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
				new ByteEntityMetadata(0, MetadataType.BYTE, (byte) 0x9C),
				new ObjectEntityMetadata<>(2, MetadataType.OPTIONAL_CHAT, Optional.empty()),
				new BooleanEntityMetadata(3, MetadataType.BOOLEAN, false),
				new ObjectEntityMetadata<>(8, MetadataType.ITEM, new ItemStack(871, 3))
		)));
		assertEquals(new ClientboundSetEntityData(8, List.of(new ShownItem(null))),
				packets.decode(data(8, new ObjectEntityMetadata<>(8, MetadataType.ITEM, null))));
	}

	@Test
	void decodesTheItemOfAnItemFrameAndOfAThrownPotion() {
		add(9, EntityType.ITEM_FRAME);
		add(10, EntityType.POTION);

		assertEquals(List.of(new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(40, 1))),
				decodeData(data(9, new ObjectEntityMetadata<>(8, MetadataType.ITEM, new ItemStack(40, 1)))));
		assertEquals(List.of(new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(5, 1))),
				decodeData(data(10, new ObjectEntityMetadata<>(8, MetadataType.ITEM, new ItemStack(5, 1)))));
	}

	@Test
	void decodesOnlyTheValuesEveryEntityKeepsForAnEntityTheSessionNeverAdded() {
		assertEquals(new ClientboundSetEntityData(42, List.of(SharedFlags.builder().sneaking(true).build())),
				packets.decode(data(42,
						new ByteEntityMetadata(0, MetadataType.BYTE, (byte) 0x02),
						new ObjectEntityMetadata<>(8, MetadataType.ITEM, new ItemStack(871, 3))
				)));
	}

	@Test
	void leavesOutAValueOfAnotherKindThanTheTableNames() {
		add(8, EntityType.ITEM);

		assertEquals(new ClientboundSetEntityData(8, List.of()), packets.decode(data(8,
				new ObjectEntityMetadata<>(8, MetadataType.CHAT, Component.text("not an item")),
				new BooleanEntityMetadata(0, MetadataType.BOOLEAN, true)
		)));
	}

	@Test
	void forgetsTheTypeOfARemovedEntityAndOfEveryEntityOfALevelThePlayerLeaves() {
		add(7, EntityType.ITEM);
		add(8, EntityType.ITEM);

		assertEquals(new ClientboundRemoveEntities(List.of(7)), packets.decode(new ClientboundRemoveEntitiesPacket(new int[]{7})));
		assertEquals(List.of(), decodeData(data(7, new ObjectEntityMetadata<>(8, MetadataType.ITEM, null))));
		assertEquals(List.of(new ShownItem(null)), decodeData(data(8, new ObjectEntityMetadata<>(8, MetadataType.ITEM, null))));

		assertNull(packets.decode(new ClientboundRespawnPacket(new CompoundTag(""), "minecraft:overworld", 0,
				GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, false)));
		assertEquals(List.of(), decodeData(data(8, new ObjectEntityMetadata<>(8, MetadataType.ITEM, null))));

		add(8, EntityType.ITEM);
		assertNull(packets.decode(new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL, 1,
				new String[0], new CompoundTag(""), new CompoundTag(""), "minecraft:overworld", 0, 20, 10, 10, false, true,
				false, false)));
		assertEquals(List.of(), decodeData(data(8, new ObjectEntityMetadata<>(8, MetadataType.ITEM, null))));
	}

	@Test
	void decodesMovesTurnsAndAbsoluteTeleports() {
		assertEquals(ClientboundMoveEntityPos.builder().entityId(7).delta(new Vec3d(0.5, -1, 0.25)).build(),
				packets.decode(new ClientboundMoveEntityPosPacket(7, 0.5, -1, 0.25, true)));
		assertEquals(ClientboundMoveEntityPosRot.builder().entityId(7).delta(new Vec3d(1, 0, 0)).yaw(90).pitch(10)
				.build(),
				packets.decode(new ClientboundMoveEntityPosRotPacket(7, 1, 0, 0, 90, 10, true)));
		assertEquals(new ClientboundMoveEntityRot(7, 180, -10),
				packets.decode(new ClientboundMoveEntityRotPacket(7, 180, -10, true)));
		assertEquals(ClientboundTeleportEntity.builder().entityId(7).position(new Vec3d(1, 2, 3)).yaw(15).pitch(5)
						.relatives(Set.of()).build(),
				packets.decode(new ClientboundTeleportEntityPacket(7, 1, 2, 3, 15, 5, true)));
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
	void readsTheSwingOfAnArmAndNoOtherAnimation() {
		assertEquals(new ClientboundSwingAnimation(7, InteractionHand.MAIN_HAND),
				packets.decode(new ClientboundAnimatePacket(7, Animation.SWING_ARM)));
		assertEquals(new ClientboundSwingAnimation(8, InteractionHand.OFF_HAND),
				packets.decode(new ClientboundAnimatePacket(8, Animation.SWING_OFFHAND)));
		assertNull(packets.decode(new ClientboundAnimatePacket(9, Animation.DAMAGE)));
	}

	@Test
	void carriesEveryEntityPacketOfTheRelease() {
		assertEquals(Set.of(
				ClientboundAddEntity.class,
				ClientboundMoveEntityPos.class,
				ClientboundMoveEntityPosRot.class,
				ClientboundMoveEntityRot.class,
				ClientboundTeleportEntity.class,
				ClientboundSetEntityData.class,
				ClientboundSetPassengers.class,
				ClientboundSwingAnimation.class,
				ClientboundRemoveEntities.class
		), packets.packets());
	}

	private void add(int entityId, EntityType type) {
		packets.decode(new ClientboundAddEntityPacket(entityId, new UUID(0, entityId), type, 0, 0, 0, 0, 0));
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
