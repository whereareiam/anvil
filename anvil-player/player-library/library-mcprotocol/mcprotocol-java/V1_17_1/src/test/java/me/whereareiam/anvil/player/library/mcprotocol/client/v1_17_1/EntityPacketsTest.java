package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityMetadataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityVelocityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerRemoveEntitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnEntityPacket;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.ShownItem;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ItemTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.TextTranslator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityPacketsTest {
	private final EntityPackets packets = new EntityPackets(new TextTranslator(), new ItemTranslator(new TextTranslator()));

	@Test
	void decodesTheItemOfAGlowItemFrameAtIndex8AfterTheFrozenTicks() {
		packets.decode(new ServerSpawnEntityPacket(7, new UUID(0, 7), EntityType.GLOW_ITEM_FRAME, 0, 0, 0, 0, 0));

		assertEquals(new ClientboundSetEntityData(new EntityId(7), List.of(
						new ShownItem(me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack.of(40, 1)))),
				packets.decode(new ServerEntityMetadataPacket(7, new EntityMetadata[]{
						new EntityMetadata(7, MetadataType.INT, 140),
						new EntityMetadata(8, MetadataType.ITEM, new ItemStack(40, 1))
				})));
	}

	@Test
	void decodesTheRemovalOfEntities() {
		assertEquals(new ClientboundRemoveEntities(List.of(new EntityId(7), new EntityId(8))),
				packets.decode(new ServerRemoveEntitiesPacket(new int[]{7, 8})));
	}

	@Test
	void decodesTheMotionOfAnEntityInBlocksPerTick() {
		assertEquals(new ClientboundSetEntityMotion(new EntityId(7), new Vec3d(0, -0.0784, 0.5)),
				packets.decode(new ServerEntityVelocityPacket(7, 0, -0.0784, 0.5)));
	}
}
