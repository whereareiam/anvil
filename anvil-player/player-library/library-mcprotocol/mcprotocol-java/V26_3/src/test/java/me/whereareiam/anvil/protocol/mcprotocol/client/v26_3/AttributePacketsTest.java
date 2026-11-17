package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The release's packet table decodes the attributes Minecraft 26.2 added by the keys the release's library gives them.
 */
class AttributePacketsTest {
	private final PacketTable<Packet> packets = McProtocolClientAdapter.packets(PacketDemand.EVERY);

	@Test
	void decodesTheAttributesTheReleaseAddedByTheirKeys() {
		assertEquals(new ClientboundUpdateAttributes(new EntityId(7), List.of(
				new AttributeSnapshot("minecraft:bounciness", 0.5, List.of()),
				new AttributeSnapshot("minecraft:friction_modifier", 1, List.of())
		)), packets.decode(new ClientboundUpdateAttributesPacket(7, List.of(
				new Attribute(AttributeType.Builtin.BOUNCINESS, 0.5, List.of()),
				new Attribute(AttributeType.Builtin.FRICTION_MODIFIER, 1, List.of())
		))));
	}
}
