package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientBehaviourTest {
	@Test
	void appliesFromTheVersionThatBroughtABehaviour() {
		assertFalse(ClientBehaviour.MINOR_HORIZONTAL_COLLISION.appliesTo(version("1.17.1")));
		assertTrue(ClientBehaviour.MINOR_HORIZONTAL_COLLISION.appliesTo(version("1.18")));
		assertTrue(ClientBehaviour.MINOR_HORIZONTAL_COLLISION.appliesTo(version("26.1.2")));
		assertFalse(ClientBehaviour.VECTOR_INPUT.appliesTo(version("1.21.1")));
		assertTrue(ClientBehaviour.VECTOR_INPUT.appliesTo(version("1.21.11")));
	}

	@Test
	void appliesUntilTheVersionThatEndedABehaviour() {
		assertTrue(ClientBehaviour.AUTO_JUMP.appliesTo(version("1.18.2")));
		assertFalse(ClientBehaviour.AUTO_JUMP.appliesTo(version("1.19")));
	}

	@Test
	void collectsThePlayersBehavioursAndItsEntitysRulesOfAVersion() {
		MovementRules old = new MovementRules(version("1.16.5"));
		MovementRules current = new MovementRules(version("1.21.11"));

		assertFalse(old.has(ClientBehaviour.VECTOR_INPUT));
		assertTrue(old.entity().has(EntityBehaviour.BOX_DRIVEN_POSITION));
		assertTrue(current.has(ClientBehaviour.VECTOR_INPUT));
		assertFalse(current.entity().has(EntityBehaviour.BOX_DRIVEN_POSITION));
	}

	private static @NotNull MinecraftVersion version(@NotNull String text) {
		return MinecraftVersion.parse(text);
	}
}
