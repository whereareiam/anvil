package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityMetadataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerRemoveEntitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnEntityPacket;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityPacketsTest {
	private final EntityPackets packets = new EntityPackets();

	@Test
	void decodesTheItemOfAGlowItemFrameAtIndex8AfterTheFrozenTicks() {
		packets.decode(new ServerSpawnEntityPacket(7, new UUID(0, 7), EntityType.GLOW_ITEM_FRAME, 0, 0, 0, 0, 0));

		assertEquals(new ClientboundSetEntityData(7, List.of(
						new ShownItem(new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(40, 1)))),
				packets.decode(new ServerEntityMetadataPacket(7, new EntityMetadata[]{
						new EntityMetadata(7, MetadataType.INT, 140),
						new EntityMetadata(8, MetadataType.ITEM, new ItemStack(40, 1))
				})));
	}

	@Test
	void decodesTheRemovalOfEntities() {
		assertEquals(new ClientboundRemoveEntities(List.of(7, 8)),
				packets.decode(new ServerRemoveEntitiesPacket(new int[]{7, 8})));
	}
}
