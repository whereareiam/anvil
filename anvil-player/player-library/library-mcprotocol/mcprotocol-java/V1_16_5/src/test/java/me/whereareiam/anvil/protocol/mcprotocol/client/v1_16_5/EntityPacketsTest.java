package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.object.HangingDirection;
import com.github.steveice10.mc.protocol.data.game.entity.player.Animation;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.data.game.entity.type.PaintingType;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityAnimationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityDestroyPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityMetadataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityTeleportPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityVelocityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnExpOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnLivingEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnPaintingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnPlayerPacket;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityMotion;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSwingAnimation;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityPacketsTest {
	private static final UUID STAND = new UUID(0, 7);

	private final EntityPackets packets = new EntityPackets();

	@Test
	void readsTheVelocityTheServerGivesAnEntity() {
		assertEquals(new ClientboundSetEntityMotion(7, new Vec3d(0.125, -0.5, 0.0)), packets.decode(new ServerEntityVelocityPacket(7, 0.125, -0.5, 0.0)));
	}

	@Test
	void readsTheSwingOfAnArmWhoseOffHandAnimationTheLibraryNamesAfterEating() {
		assertEquals(new ClientboundSwingAnimation(7, InteractionHand.MAIN_HAND),
				packets.decode(new ServerEntityAnimationPacket(7, Animation.SWING_ARM)));
		assertEquals(new ClientboundSwingAnimation(8, InteractionHand.OFF_HAND),
				packets.decode(new ServerEntityAnimationPacket(8, Animation.EAT_FOOD)));
		assertNull(packets.decode(new ServerEntityAnimationPacket(9, Animation.DAMAGE)));
	}

	@Test
	void decodesEachSpawnPacketAsAnAddedEntityWithTheRegistryKey() {
		assertEquals(ClientboundAddEntity.builder().entityId(7).uniqueId(STAND)
						.type(EntityTypeKey.minecraft("armor_stand")).position(new Vec3d(1.5, 64, -2.5)).yaw(90)
						.pitch(10).build(),
				packets.decode(new ServerSpawnEntityPacket(7, STAND, EntityType.ARMOR_STAND, 1.5, 64, -2.5, 90, 10)));
		assertEquals(ClientboundAddEntity.builder().entityId(8).uniqueId(new UUID(0, 8))
						.type(EntityTypeKey.minecraft("zombie")).position(new Vec3d(0, 66, 0)).yaw(180).pitch(20)
						.build(),
				packets.decode(new ServerSpawnLivingEntityPacket(8, new UUID(0, 8), EntityType.ZOMBIE, 0, 66, 0, 20,
						180, 45, 0, 0, 0)));
		assertEquals(ClientboundAddEntity.builder().entityId(9).uniqueId(new UUID(0, 9))
						.type(EntityTypeKey.minecraft("player")).position(new Vec3d(2, 64, 2)).yaw(270).pitch(-30)
						.build(),
				packets.decode(new ServerSpawnPlayerPacket(9, new UUID(0, 9), 2, 64, 2, 270, -30)));
		assertEquals(ClientboundAddEntity.builder().entityId(10).uniqueId(new UUID(0, 10))
						.type(EntityTypeKey.minecraft("painting")).position(new Vec3d(4, 65, 4)).build(),
				packets.decode(new ServerSpawnPaintingPacket(10, new UUID(0, 10), PaintingType.KEBAB,
						new Position(4, 65, 4), HangingDirection.NORTH)));
		assertEquals(ClientboundAddEntity.builder().entityId(11).type(EntityTypeKey.minecraft("experience_orb"))
						.position(new Vec3d(1, 2, 3)).build(),
				packets.decode(new ServerSpawnExpOrbPacket(11, 1, 2, 3, 5)));
	}

	@Test
	void namesTheTypesTheLibraryNamesAfterOlderNamesByTheirRegistryKeys() {
		assertEquals(EntityTypeKey.minecraft("ender_pearl"), added(12, EntityType.THROWN_ENDERPEARL).getType());
		assertEquals(EntityTypeKey.minecraft("tnt"), added(13, EntityType.PRIMED_TNT).getType());
		assertEquals(EntityTypeKey.minecraft("chest_minecart"), added(14, EntityType.MINECART_CHEST).getType());
	}

	@Test
	void decodesTheValuesEveryEntityKeepsAndTheItemOfADroppedItemAtIndex7() {
		added(15, EntityType.ITEM);

		assertEquals(List.of(
				SharedFlags.builder().onFire(true).build(),
				new CustomName("Alice: hello"),
				new CustomNameVisible(true),
				new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(871, 3))
		), decodeData(data(15,
				new EntityMetadata(0, MetadataType.BYTE, (byte) 0x01),
				new EntityMetadata(2, MetadataType.OPTIONAL_CHAT,
						Component.text("Alice: ").append(Component.text("hello"))),
				new EntityMetadata(3, MetadataType.BOOLEAN, true),
				new EntityMetadata(7, MetadataType.ITEM, new ItemStack(871, 3)),
				new EntityMetadata(8, MetadataType.ITEM, new ItemStack(1, 1)))));
	}

	@Test
	void decodesAnAbsentCustomNameAsARemovedName() {
		added(16, EntityType.ARMOR_STAND);

		assertEquals(List.of(new CustomName(null)),
				decodeData(data(16, new EntityMetadata(2, MetadataType.OPTIONAL_CHAT, null))));
	}

	@Test
	void decodesMovesTeleportsAndRemovals() {
		assertEquals(ClientboundMoveEntityPos.builder().entityId(7).delta(new Vec3d(0.5, 0, -0.25)).build(),
				packets.decode(new ServerEntityPositionPacket(7, 0.5, 0, -0.25, true)));
		assertEquals(ClientboundTeleportEntity.builder().entityId(7).position(new Vec3d(1, 2, 3)).yaw(90).pitch(0)
						.relatives(Set.of()).build(),
				packets.decode(new ServerEntityTeleportPacket(7, 1, 2, 3, 90, 0, true)));
		assertEquals(new ClientboundRemoveEntities(List.of(7, 8)),
				packets.decode(new ServerEntityDestroyPacket(new int[]{7, 8})));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
	}

	private ClientboundAddEntity added(int entityId, EntityType type) {
		return assertInstanceOf(ClientboundAddEntity.class, packets.decode(new ServerSpawnEntityPacket(entityId,
				new UUID(0, entityId), type, 0, 0, 0, 0, 0)));
	}

	private List<EntityDataValue> decodeData(ServerEntityMetadataPacket data) {
		return assertInstanceOf(ClientboundSetEntityData.class, packets.decode(data)).getValues();
	}

	private static ServerEntityMetadataPacket data(int entityId, EntityMetadata... metadata) {
		return new ServerEntityMetadataPacket(entityId, metadata);
	}
}
