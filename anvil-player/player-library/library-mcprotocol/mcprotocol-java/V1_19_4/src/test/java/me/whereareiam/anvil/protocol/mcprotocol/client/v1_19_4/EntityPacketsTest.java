package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPlayerPacket;
import me.whereareiam.anvil.protocol.java.api.model.entity.DisplayText;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityPacketsTest {
	private static final UUID ZOMBIE = new UUID(0, 7);

	private final EntityPackets packets = new EntityPackets();

	@Test
	void decodesMobsAndPaintingsThroughTheEntitySpawnAndPlayersAndOrbsThroughTheirOwn() {
		assertEquals(ClientboundAddEntity.builder().entityId(7).uniqueId(ZOMBIE).type(EntityTypeKey.minecraft("zombie"))
						.position(new Vec3d(1.5, 64, -2.5)).yaw(90).pitch(10).build(),
				packets.decode(new ClientboundAddEntityPacket(7, ZOMBIE, EntityType.ZOMBIE, 1.5, 64, -2.5, 90, 10,
						90)));
		assertEquals(ClientboundAddEntity.builder().entityId(8).uniqueId(new UUID(0, 8))
						.type(EntityTypeKey.minecraft("painting")).position(new Vec3d(4, 65, 4)).build(),
				packets.decode(new ClientboundAddEntityPacket(8, new UUID(0, 8), EntityType.PAINTING, 4, 65, 4, 0, 0,
						0)));
		assertEquals(ClientboundAddEntity.builder().entityId(9).uniqueId(new UUID(0, 9))
						.type(EntityTypeKey.minecraft("player")).position(new Vec3d(2, 64, 2)).yaw(270).pitch(-30)
						.build(),
				packets.decode(new ClientboundAddPlayerPacket(9, new UUID(0, 9), 2, 64, 2, 270, -30)));
		assertEquals(ClientboundAddEntity.builder().entityId(10).type(EntityTypeKey.minecraft("experience_orb"))
						.position(new Vec3d(1, 2, 3)).build(),
				packets.decode(new ClientboundAddExperienceOrbPacket(10, 1, 2, 3, 5)));
	}

	@Test
	void decodesTheTextAndItemOfDisplaysAtTheirReleaseIndex() {
		add(11, EntityType.TEXT_DISPLAY);
		add(12, EntityType.ITEM_DISPLAY);

		assertEquals(List.of(new DisplayText("Welcome Alice")), decodeData(data(11,
				new ObjectEntityMetadata<>(22, MetadataType.CHAT,
						Component.text("Welcome ").append(Component.text("Alice"))),
				new ObjectEntityMetadata<>(23, MetadataType.CHAT, Component.text("not the text")))));
		assertEquals(List.of(new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(5, 2))),
				decodeData(data(12,
						new ObjectEntityMetadata<>(22, MetadataType.ITEM, new ItemStack(5, 2)),
						new ObjectEntityMetadata<>(23, MetadataType.ITEM, new ItemStack(6, 1)))));
	}

	@Test
	void decodesTheItemOfADroppedItemAndTheFlagsEveryEntityKeeps() {
		add(13, EntityType.ITEM);

		assertEquals(List.of(
				SharedFlags.builder().onFire(true).build(),
				new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(871, 3))
		), decodeData(data(13,
				new ByteEntityMetadata(0, MetadataType.BYTE, (byte) 0x01),
				new ObjectEntityMetadata<>(8, MetadataType.ITEM, new ItemStack(871, 3)))));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
	}

	private void add(int entityId, EntityType type) {
		packets.decode(new ClientboundAddEntityPacket(entityId, new UUID(0, entityId), type, 0, 0, 0, 0, 0, 0));
	}

	private List<EntityDataValue> decodeData(ClientboundSetEntityDataPacket data) {
		return assertInstanceOf(ClientboundSetEntityData.class, packets.decode(data)).getValues();
	}

	private static ClientboundSetEntityDataPacket data(int entityId, EntityMetadata<?, ?>... metadata) {
		return new ClientboundSetEntityDataPacket(entityId, metadata);
	}
}
