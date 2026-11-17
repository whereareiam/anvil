package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.Effect;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.Attribute;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeModifier;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.AttributeType;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.ModifierOperation;
import com.github.steveice10.mc.protocol.data.game.entity.attribute.ModifierType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityStatePacketsTest {
	private final EntityStatePackets packets = new EntityStatePackets();

	@Test
	void decodesAttributesInTheNamesOfMinecraft1212WithEachModifierIdentifiedByItsUuid() {
		UUID boost = new UUID(0, 3);
		ClientboundUpdateAttributesPacket attributes = new ClientboundUpdateAttributesPacket(7, List.of(
				new Attribute(AttributeType.Builtin.GENERIC_MOVEMENT_SPEED, 0.1, List.of(
						new AttributeModifier(ModifierType.SPRINT_SPEED_BOOST, 0.3, ModifierOperation.MULTIPLY),
						new AttributeModifier(boost, 0.2, ModifierOperation.ADD_MULTIPLIED))),
				new Attribute(AttributeType.Builtin.GENERIC_ARMOR, 2, List.of(
						new AttributeModifier(boost, 1, ModifierOperation.ADD))),
				new Attribute(AttributeType.Builtin.HORSE_JUMP_STRENGTH, 0.7),
				new Attribute(AttributeType.Builtin.ZOMBIE_SPAWN_REINFORCEMENTS, 0.05)));

		assertEquals(new ClientboundUpdateAttributes(7, List.of(
				new AttributeSnapshot("minecraft:movement_speed", 0.1, List.of(
						modifier(ModifierType.SPRINT_SPEED_BOOST, 0.3, AttributeOperation.ADD_MULTIPLIED_TOTAL),
						modifier(boost, 0.2, AttributeOperation.ADD_MULTIPLIED_BASE))),
				new AttributeSnapshot("minecraft:armor", 2, List.of(modifier(boost, 1, AttributeOperation.ADD_VALUE))),
				new AttributeSnapshot("minecraft:jump_strength", 0.7, List.of()),
				new AttributeSnapshot("minecraft:spawn_reinforcements", 0.05, List.of()))), packets.decode(attributes));
	}

	@Test
	void keepsTheNameOfAnAttributeOfAnotherNamespaceAndOfAMinecraftAttributeWithoutAReleasePrefix() {
		ClientboundUpdateAttributesPacket attributes = new ClientboundUpdateAttributesPacket(7, List.of(
				new Attribute(new AttributeType.Custom("example:generic.mana"), 3),
				new Attribute(new AttributeType.Custom("minecraft:gravity"), 0.08)));

		assertEquals(new ClientboundUpdateAttributes(7, List.of(
				new AttributeSnapshot("example:generic.mana", 3, List.of()),
				new AttributeSnapshot("minecraft:gravity", 0.08, List.of()))), packets.decode(attributes));
	}

	@Test
	void decodesAnEffectByItsRegistryKey() {
		assertEquals(ClientboundUpdateMobEffect.builder().entityId(7).effect("minecraft:jump_boost").amplifier(1)
						.duration(600).build(),
				packets.decode(new ClientboundUpdateMobEffectPacket(7, Effect.JUMP_BOOST, 1, 600, true, true)));
		assertEquals(ClientboundUpdateMobEffect.builder().entityId(7).effect("minecraft:resistance").amplifier(0)
						.duration(-1).build(),
				packets.decode(new ClientboundUpdateMobEffectPacket(7, Effect.RESISTANCE, 0, -1, false, false)));
	}

	@Test
	void decodesTheRemovalOfAnEffectByItsRegistryKey() {
		assertEquals(new ClientboundRemoveMobEffect(7, "minecraft:hero_of_the_village"),
				packets.decode(new ClientboundRemoveMobEffectPacket(7, Effect.HERO_OF_THE_VILLAGE)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheAttributesAndEffectsOfEntities() {
		assertEquals(Set.of(ClientboundUpdateAttributes.class, ClientboundUpdateMobEffect.class,
				ClientboundRemoveMobEffect.class), packets.packets());
	}

	private static me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier modifier(
			UUID id,
			double amount,
			AttributeOperation operation
	) {
		return new me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier(id.toString(), amount,
				operation);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
