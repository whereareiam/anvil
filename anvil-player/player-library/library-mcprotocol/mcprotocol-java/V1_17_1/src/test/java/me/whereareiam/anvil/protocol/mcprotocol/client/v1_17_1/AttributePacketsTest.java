package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.attribute.Attribute;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeType;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.ModifierOperation;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityPropertiesPacket;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.AttributePackets;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The attribute translation of the 1.16 chain on the MCProtocolLib 1.17.1 release, whose library keeps the attribute
 * keys of Minecraft 1.16 with their categories.
 */
class AttributePacketsTest {
	private static final UUID SPRINTING = UUID.fromString("662a6b8d-da3e-4c1c-8813-96ea6097278d");

	private final AttributePackets packets = new AttributePackets();

	@Test
	void decodesTheAttributesInTheNamesOfMinecraft1212WithTheirModifiers() {
		assertEquals(new ClientboundUpdateAttributes(7, List.of(
				new AttributeSnapshot("minecraft:movement_speed", 0.1, List.of(
						new AttributeModifier(SPRINTING.toString(), 0.3, AttributeOperation.ADD_MULTIPLIED_TOTAL))),
				new AttributeSnapshot("minecraft:jump_strength", 0.7, List.of()),
				new AttributeSnapshot("minecraft:spawn_reinforcements", 0.05, List.of()),
				new AttributeSnapshot("forge:entity_gravity", 0.08, List.of())
		)), packets.decode(new ServerEntityPropertiesPacket(7, List.of(
				new Attribute(AttributeType.GENERIC_MOVEMENT_SPEED, 0.1, List.of(
						new com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeModifier(SPRINTING,
								0.3, ModifierOperation.MULTIPLY))),
				new Attribute(AttributeType.HORSE_JUMP_STRENGTH, 0.7),
				new Attribute(AttributeType.ZOMBIE_SPAWN_REINFORCEMENTS, 0.05),
				new Attribute(AttributeType.ENTITY_GRAVITY, 0.08)
		))));
	}
}
