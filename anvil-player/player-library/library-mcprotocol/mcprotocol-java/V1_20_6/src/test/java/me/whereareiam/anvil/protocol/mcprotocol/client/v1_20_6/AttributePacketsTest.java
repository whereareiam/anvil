package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttributePacketsTest {
	private static final UUID SPRINTING = UUID.fromString("662a6b8d-da3e-4c1c-8813-96ea6097278d");
	private static final UUID BOOST = new UUID(0, 7);

	private final AttributePackets packets = new AttributePackets();

	@Test
	void decodesAttributesInTheNamesOfMinecraft1212WithModifiersNamedByTheirUuids() {
		ClientboundUpdateAttributesPacket update = new ClientboundUpdateAttributesPacket(42, List.of(
				new Attribute(AttributeType.Builtin.GENERIC_MOVEMENT_SPEED, 0.1, List.of(
						new org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier(SPRINTING,
								0.3, ModifierOperation.ADD_MULTIPLIED_TOTAL),
						new org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier(BOOST,
								0.5, ModifierOperation.ADD_MULTIPLIED_BASE),
						new org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier(BOOST,
								1, ModifierOperation.ADD))),
				new Attribute(AttributeType.Builtin.PLAYER_BLOCK_INTERACTION_RANGE, 4.5),
				new Attribute(AttributeType.Builtin.ZOMBIE_SPAWN_REINFORCEMENTS, 0.05)));

		assertEquals(new ClientboundUpdateAttributes(42, List.of(
				new AttributeSnapshot("minecraft:movement_speed", 0.1, List.of(
						new AttributeModifier(SPRINTING.toString(), 0.3, AttributeOperation.ADD_MULTIPLIED_TOTAL),
						new AttributeModifier(BOOST.toString(), 0.5, AttributeOperation.ADD_MULTIPLIED_BASE),
						new AttributeModifier(BOOST.toString(), 1, AttributeOperation.ADD_VALUE))),
				new AttributeSnapshot("minecraft:block_interaction_range", 4.5, List.of()),
				new AttributeSnapshot("minecraft:spawn_reinforcements", 0.05, List.of())
		)), packets.decode(update));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheAttributePacket() {
		assertEquals(Set.of(ClientboundUpdateAttributes.class), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
