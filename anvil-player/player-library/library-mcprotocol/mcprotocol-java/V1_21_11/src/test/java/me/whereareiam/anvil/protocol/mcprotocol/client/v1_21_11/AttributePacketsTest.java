package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The release's packet table decodes the attributes of entities, which the release's library names as Minecraft
 * 1.21.2 does, with each modifier by its key.
 */
class AttributePacketsTest {
	private final PacketTable<Packet> packets = McProtocolClientAdapter.packets(PacketDemand.EVERY);

	@Test
	void decodesAttributesByTheirKeysAndModifiersByTheirKeys() {
		assertEquals(new ClientboundUpdateAttributes(new EntityId(42), List.of(
				new AttributeSnapshot("minecraft:movement_speed", 0.1, List.of(
						new AttributeModifier("minecraft:sprinting", 0.3, AttributeOperation.ADD_MULTIPLIED_TOTAL),
						new AttributeModifier("example:boots", 0.05, AttributeOperation.ADD_VALUE),
						new AttributeModifier("example:potion", 0.2, AttributeOperation.ADD_MULTIPLIED_BASE))),
				new AttributeSnapshot("minecraft:block_interaction_range", 4.5, List.of())
		)), packets.decode(new ClientboundUpdateAttributesPacket(42, List.of(
				new Attribute(AttributeType.Builtin.MOVEMENT_SPEED, 0.1, List.of(
						modifier(Key.key("minecraft", "sprinting"), 0.3, ModifierOperation.ADD_MULTIPLIED_TOTAL),
						modifier(Key.key("example", "boots"), 0.05, ModifierOperation.ADD),
						modifier(Key.key("example", "potion"), 0.2, ModifierOperation.ADD_MULTIPLIED_BASE))),
				new Attribute(AttributeType.Builtin.BLOCK_INTERACTION_RANGE, 4.5, List.of())
		))));
	}

	@Test
	void carriesTheAttributeUpdate() {
		assertTrue(packets.supports(ClientboundUpdateAttributes.class));
	}

	private static org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier modifier(
			Key id,
			double amount,
			ModifierOperation operation
	) {
		return new org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier(id, amount, operation);
	}
}
