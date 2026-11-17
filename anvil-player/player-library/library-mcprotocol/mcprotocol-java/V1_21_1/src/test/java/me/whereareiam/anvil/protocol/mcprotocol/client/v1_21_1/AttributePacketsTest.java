package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttributePacketsTest {
	private final AttributePackets packets = new AttributePackets();

	@Test
	void decodesAttributesInTheNamesOfMinecraft1212WithModifiersNamedByTheirKeys() {
		ClientboundUpdateAttributesPacket update = new ClientboundUpdateAttributesPacket(42, List.of(
				new Attribute(AttributeType.Builtin.GENERIC_MOVEMENT_SPEED, 0.1, List.of(
						new org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier(
								Key.key("minecraft", "sprinting"), 0.3, ModifierOperation.ADD_MULTIPLIED_TOTAL),
						new org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier(
								Key.key("example", "boost"), 1, ModifierOperation.ADD))),
				new Attribute(AttributeType.Builtin.PLAYER_SNEAKING_SPEED, 0.3),
				new Attribute(AttributeType.Builtin.GENERIC_WATER_MOVEMENT_EFFICIENCY, 0)));

		assertEquals(new ClientboundUpdateAttributes(new EntityId(42), List.of(
				new AttributeSnapshot("minecraft:movement_speed", 0.1, List.of(
						new AttributeModifier("minecraft:sprinting", 0.3, AttributeOperation.ADD_MULTIPLIED_TOTAL),
						new AttributeModifier("example:boost", 1, AttributeOperation.ADD_VALUE))),
				new AttributeSnapshot("minecraft:sneaking_speed", 0.3, List.of()),
				new AttributeSnapshot("minecraft:water_movement_efficiency", 0, List.of())
		)), packets.decode(update));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
	}

	@Test
	void carriesTheAttributePacket() {
		assertEquals(Set.of(ClientboundUpdateAttributes.class), packets.packets());
	}
}
