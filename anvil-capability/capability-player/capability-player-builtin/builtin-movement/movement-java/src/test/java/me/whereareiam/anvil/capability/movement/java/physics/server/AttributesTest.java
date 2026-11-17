package me.whereareiam.anvil.capability.movement.java.physics.server;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributesTest {
	@Test
	void startsAtThePlayersVanillaDefaults() {
		Attributes attributes = new Attributes(MinecraftVersion.parse("1.21.11"));

		assertEquals(0.1F, attributes.value(PlayerAttribute.MOVEMENT_SPEED));
		assertEquals(0.42F, attributes.value(PlayerAttribute.JUMP_STRENGTH));
		assertEquals(0.08, attributes.value(PlayerAttribute.GRAVITY));
		assertEquals(0.6, attributes.value(PlayerAttribute.STEP_HEIGHT));
		assertEquals(0.3, attributes.value(PlayerAttribute.SNEAKING_SPEED));
	}

	@Test
	void appliesTheModifiersByOperationInVanillasOrderAndClampsTheValue() {
		Attributes attributes = new Attributes(MinecraftVersion.parse("1.21.11"));

		attributes.update(List.of(new AttributeSnapshot("minecraft:movement_speed", 0.1, List.of(
				new AttributeModifier("a", 0.5, AttributeOperation.ADD_MULTIPLIED_TOTAL),
				new AttributeModifier("b", 0.2, AttributeOperation.ADD_MULTIPLIED_BASE),
				new AttributeModifier("c", 0.05, AttributeOperation.ADD_VALUE))),
				new AttributeSnapshot("minecraft:gravity", 3.0, List.of())));

		double added = 0.1 + 0.05;
		assertEquals((added + added * 0.2) * (1.0 + 0.5), attributes.value(PlayerAttribute.MOVEMENT_SPEED));
		assertEquals(1.0, attributes.value(PlayerAttribute.GRAVITY));
	}

	@Test
	void addsTheSprintingModifierUnderTheIdOfTheClientsVersion() {
		Attributes modern = new Attributes(MinecraftVersion.parse("1.21.11"));
		Attributes legacy = new Attributes(MinecraftVersion.parse("1.20.6"));
		legacy.update(List.of(new AttributeSnapshot("minecraft:movement_speed", 0.1F, List.of(new AttributeModifier(
				"662a6b8d-da3e-4c1c-8813-96ea6097278d", 0.3F, AttributeOperation.ADD_MULTIPLIED_TOTAL)))));

		modern.sprinting(true);
		legacy.sprinting(true);

		assertEquals(0.1F * (1.0 + 0.3F), modern.value(PlayerAttribute.MOVEMENT_SPEED));
		assertEquals(0.1F * (1.0 + 0.3F), legacy.value(PlayerAttribute.MOVEMENT_SPEED));
		modern.sprinting(false);
		assertEquals(0.1F, modern.value(PlayerAttribute.MOVEMENT_SPEED));
	}

	@Test
	void ignoresTheAttributesTheClientsVersionDoesNotHave() {
		Attributes attributes = new Attributes(MinecraftVersion.parse("1.19.4"));

		attributes.update(List.of(new AttributeSnapshot("minecraft:gravity", 0.04, List.of()),
				new AttributeSnapshot("minecraft:sneaking_speed", 1.0, List.of())));

		assertEquals(0.08, attributes.value(PlayerAttribute.GRAVITY));
		assertEquals(0.3, attributes.value(PlayerAttribute.SNEAKING_SPEED));
	}

	@Test
	void keepsOnlyTheBaseValuesForARespawnedPlayer() {
		Attributes attributes = new Attributes(MinecraftVersion.parse("1.21.11"));
		attributes.update(List.of(new AttributeSnapshot("minecraft:movement_speed", 0.2, List.of(
				new AttributeModifier("minecraft:effect.speed", 1.0, AttributeOperation.ADD_MULTIPLIED_TOTAL)))));

		attributes.keepBaseValues();

		assertEquals(0.2, attributes.value(PlayerAttribute.MOVEMENT_SPEED));
	}
}
