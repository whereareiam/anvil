package me.whereareiam.anvil.protocol.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.geometry.Trigonometry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRulesTest {
	@Test
	void appliesFromTheVersionThatBroughtABehaviour() {
		assertFalse(EntityBehaviour.SUPPORTING_BLOCK.appliesTo(version("1.19.4")));
		assertTrue(EntityBehaviour.SUPPORTING_BLOCK.appliesTo(version("1.20")));
		assertTrue(EntityBehaviour.SUPPORTING_BLOCK.appliesTo(version("26.1.2")));
		assertFalse(EntityBehaviour.DOUBLE_FALL_DISTANCE.appliesTo(version("1.21.1")));
		assertTrue(EntityBehaviour.DOUBLE_FALL_DISTANCE.appliesTo(version("1.21.11")));
	}

	@Test
	void appliesUntilTheVersionThatEndedABehaviour() {
		assertTrue(EntityBehaviour.BOX_DRIVEN_POSITION.appliesTo(version("1.16.5")));
		assertFalse(EntityBehaviour.BOX_DRIVEN_POSITION.appliesTo(version("1.17")));
		assertTrue(EntityBehaviour.LOWERED_EYE_FLUID_TEST.appliesTo(version("1.20.6")));
		assertFalse(EntityBehaviour.LOWERED_EYE_FLUID_TEST.appliesTo(version("1.21.1")));
		assertTrue(EntityBehaviour.DAMPED_INPUT.appliesTo(version("1.21.1")));
		assertFalse(EntityBehaviour.DAMPED_INPUT.appliesTo(version("1.21.11")));
	}

	@Test
	void collectsTheBehavioursOfAVersionIntoItsRules() {
		EntityRules old = new EntityRules(version("1.16.5"));
		EntityRules current = new EntityRules(version("1.21.11"));

		assertTrue(old.has(EntityBehaviour.BOX_DRIVEN_POSITION));
		assertFalse(old.has(EntityBehaviour.CANDIDATE_STEP_HEIGHTS));
		assertEquals(Trigonometry.FLOAT, old.trigonometry());
		assertTrue(current.has(EntityBehaviour.CANDIDATE_STEP_HEIGHTS));
		assertFalse(current.has(EntityBehaviour.BOX_DRIVEN_POSITION));
		assertEquals(Trigonometry.DOUBLE, current.trigonometry());
	}

	private static @NotNull MinecraftVersion version(@NotNull String text) {
		return MinecraftVersion.parse(text);
	}
}
