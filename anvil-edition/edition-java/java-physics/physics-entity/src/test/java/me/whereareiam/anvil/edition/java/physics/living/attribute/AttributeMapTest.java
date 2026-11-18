package me.whereareiam.anvil.edition.java.physics.living.attribute;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.edition.java.protocol.type.entity.AttributeOperation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttributeMapTest {
	private static final Attribute REACH = Attribute.of("example:reach", "1.21", 3.0, 0.0, 10.0);

	@Test
	void startsAtTheBasesTheKindOfEntityGivesIt() {
		AttributeMap attributes = living("1.21.11");
		AttributeMap faster = new AttributeMap(version("1.21.11"),
				AttributeSupplier.living().with(LivingAttributes.MOVEMENT_SPEED, 0.1F));

		assertEquals(0.7, attributes.value(LivingAttributes.MOVEMENT_SPEED));
		assertEquals(0.42F, attributes.value(LivingAttributes.JUMP_STRENGTH));
		assertEquals(0.08, attributes.value(LivingAttributes.GRAVITY));
		assertEquals(0.6, attributes.value(LivingAttributes.STEP_HEIGHT));
		assertEquals(0.1F, faster.value(LivingAttributes.MOVEMENT_SPEED));
	}

	@Test
	void appliesTheModifiersByOperationInVanillasOrderAndClampsTheValue() {
		AttributeMap attributes = living("1.21.11");

		attributes.update(List.of(new AttributeSnapshot("minecraft:movement_speed", 0.1, List.of(
				new AttributeModifier("a", 0.5, AttributeOperation.ADD_MULTIPLIED_TOTAL),
				new AttributeModifier("b", 0.2, AttributeOperation.ADD_MULTIPLIED_BASE),
				new AttributeModifier("c", 0.05, AttributeOperation.ADD_VALUE))),
				new AttributeSnapshot("minecraft:gravity", 3.0, List.of())));

		double added = 0.1 + 0.05;
		assertEquals((added + added * 0.2) * (1.0 + 0.5), attributes.value(LivingAttributes.MOVEMENT_SPEED));
		assertEquals(1.0, attributes.value(LivingAttributes.GRAVITY));
	}

	@Test
	void addsTheSprintingModifierUnderTheIdOfTheClientsVersion() {
		AttributeMap modern = living("1.21.11");
		AttributeMap legacy = living("1.20.6");
		modern.update(List.of(new AttributeSnapshot("minecraft:movement_speed", 0.1F, List.of())));
		legacy.update(List.of(new AttributeSnapshot("minecraft:movement_speed", 0.1F, List.of(new AttributeModifier(
				"662a6b8d-da3e-4c1c-8813-96ea6097278d", 0.3F, AttributeOperation.ADD_MULTIPLIED_TOTAL)))));

		modern.sprinting(true);
		legacy.sprinting(true);

		assertEquals(0.1F * (1.0 + 0.3F), modern.value(LivingAttributes.MOVEMENT_SPEED));
		assertEquals(0.1F * (1.0 + 0.3F), legacy.value(LivingAttributes.MOVEMENT_SPEED));
		modern.sprinting(false);
		assertEquals(0.1F, modern.value(LivingAttributes.MOVEMENT_SPEED));
	}

	@Test
	void ignoresTheAttributesTheClientsVersionOrTheEntitysKindDoesNotHave() {
		AttributeMap older = new AttributeMap(version("1.19.4"), AttributeSupplier.living().with(REACH));
		AttributeMap current = living("1.21.11");

		older.update(List.of(new AttributeSnapshot("minecraft:gravity", 0.04, List.of()),
				new AttributeSnapshot("example:reach", 5.0, List.of())));
		current.update(List.of(new AttributeSnapshot("example:reach", 5.0, List.of())));

		assertEquals(0.08, older.value(LivingAttributes.GRAVITY));
		assertEquals(3.0, older.value(REACH));
		assertThrows(IllegalArgumentException.class, () -> current.value(REACH));
	}

	@Test
	void keepsOnlyTheBaseValuesForARespawnedEntity() {
		AttributeMap attributes = living("1.21.11");
		attributes.update(List.of(new AttributeSnapshot("minecraft:movement_speed", 0.2, List.of(
				new AttributeModifier("minecraft:effect.speed", 1.0, AttributeOperation.ADD_MULTIPLIED_TOTAL)))));

		attributes.keepBaseValues();

		assertEquals(0.2, attributes.value(LivingAttributes.MOVEMENT_SPEED));
	}

	private static @NotNull AttributeMap living(@NotNull String version) {
		return new AttributeMap(version(version), AttributeSupplier.living());
	}

	private static @NotNull MinecraftVersion version(@NotNull String text) {
		return MinecraftVersion.parse(text);
	}
}
