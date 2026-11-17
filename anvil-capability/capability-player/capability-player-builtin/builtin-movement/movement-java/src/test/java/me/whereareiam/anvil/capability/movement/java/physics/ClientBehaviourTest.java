package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Trigonometry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientBehaviourTest {
	@Test
	void appliesFromTheVersionThatBroughtABehaviour() {
		assertFalse(ClientBehaviour.SUPPORTING_BLOCK.appliesTo(version("1.19.4")));
		assertTrue(ClientBehaviour.SUPPORTING_BLOCK.appliesTo(version("1.20")));
		assertTrue(ClientBehaviour.SUPPORTING_BLOCK.appliesTo(version("26.1.2")));
		assertFalse(ClientBehaviour.VECTOR_INPUT.appliesTo(version("1.21.1")));
		assertTrue(ClientBehaviour.VECTOR_INPUT.appliesTo(version("1.21.11")));
	}

	@Test
	void appliesUntilTheVersionThatEndedABehaviour() {
		assertTrue(ClientBehaviour.BOX_DRIVEN_POSITION.appliesTo(version("1.16.5")));
		assertFalse(ClientBehaviour.BOX_DRIVEN_POSITION.appliesTo(version("1.17")));
		assertTrue(ClientBehaviour.TELEPORT_CONFIRMATION.appliesTo(version("26.2")));
		assertFalse(ClientBehaviour.TELEPORT_CONFIRMATION.appliesTo(version("26.3")));
		assertTrue(ClientBehaviour.LOWERED_EYE_FLUID_TEST.appliesTo(version("1.20.6")));
		assertFalse(ClientBehaviour.LOWERED_EYE_FLUID_TEST.appliesTo(version("1.21.1")));
	}

	@Test
	void collectsTheBehavioursOfAVersionIntoItsRules() {
		MovementRules old = new MovementRules(version("1.16.5"));
		MovementRules current = new MovementRules(version("1.21.11"));

		assertTrue(old.has(ClientBehaviour.BOX_DRIVEN_POSITION));
		assertFalse(old.has(ClientBehaviour.CANDIDATE_STEP_HEIGHTS));
		assertEquals(Trigonometry.FLOAT, old.trigonometry());
		assertTrue(current.has(ClientBehaviour.CANDIDATE_STEP_HEIGHTS));
		assertFalse(current.has(ClientBehaviour.BOX_DRIVEN_POSITION));
		assertEquals(Trigonometry.DOUBLE, current.trigonometry());
	}

	private static @NotNull MinecraftVersion version(@NotNull String text) {
		return MinecraftVersion.parse(text);
	}
}
