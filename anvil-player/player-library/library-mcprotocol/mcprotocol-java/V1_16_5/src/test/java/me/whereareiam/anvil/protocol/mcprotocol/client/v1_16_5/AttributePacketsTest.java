package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.attribute.Attribute;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeType;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.ModifierOperation;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityPropertiesPacket;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.edition.java.protocol.type.entity.AttributeOperation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttributePacketsTest {
	private static final UUID SPRINTING = UUID.fromString("662a6b8d-da3e-4c1c-8813-96ea6097278d");
	private static final UUID BOOTS = UUID.fromString("845db27c-c624-495f-8c9f-6020a9a58b6b");
	private static final UUID POTION = UUID.fromString("91aeaa56-376b-4498-935b-2f7f68070635");

	private final AttributePackets packets = new AttributePackets();

	@Test
	void decodesEachAttributeWithItsModifiersInTheServersOrder() {
		assertEquals(new ClientboundUpdateAttributes(new EntityId(7), List.of(
				new AttributeSnapshot("minecraft:movement_speed", 0.1, List.of(
						new AttributeModifier(SPRINTING.toString(), 0.3, AttributeOperation.ADD_MULTIPLIED_TOTAL),
						new AttributeModifier(BOOTS.toString(), 2, AttributeOperation.ADD_VALUE),
						new AttributeModifier(POTION.toString(), 0.2, AttributeOperation.ADD_MULTIPLIED_BASE))),
				new AttributeSnapshot("minecraft:max_health", 20, List.of())
		)), packets.decode(new ServerEntityPropertiesPacket(7, List.of(
				new Attribute(AttributeType.GENERIC_MOVEMENT_SPEED, 0.1, List.of(
						new com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeModifier(SPRINTING,
								0.3, ModifierOperation.MULTIPLY),
						new com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeModifier(BOOTS, 2,
								ModifierOperation.ADD),
						new com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeModifier(POTION, 0.2,
								ModifierOperation.ADD_MULTIPLIED))),
				new Attribute(AttributeType.GENERIC_MAX_HEALTH, 20)
		))));
	}

	@Test
	void namesEveryVanillaAttributeAsMinecraft1212DoesAndKeepsOtherNamespaces() {
		assertEquals(List.of(
				"minecraft:max_health",
				"minecraft:follow_range",
				"minecraft:knockback_resistance",
				"minecraft:movement_speed",
				"minecraft:attack_damage",
				"minecraft:attack_speed",
				"minecraft:flying_speed",
				"minecraft:armor",
				"minecraft:armor_toughness",
				"minecraft:attack_knockback",
				"minecraft:luck",
				"minecraft:jump_strength",
				"minecraft:spawn_reinforcements",
				"forge:entity_gravity"
		), Arrays.stream(AttributeType.values()).map(this::key).toList());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheAttributesOfEntities() {
		assertEquals(Set.of(ClientboundUpdateAttributes.class), packets.packets());
	}

	private String key(AttributeType type) {
		return assertInstanceOf(ClientboundUpdateAttributes.class, packets.decode(
				new ServerEntityPropertiesPacket(7, List.of(new Attribute(type, 0))))).getAttributes().getFirst()
				.getAttribute();
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
