package me.whereareiam.anvil.capability.world.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeenDimensionTest {
	@Test
	void spansTheHeightsOfItsLevel() {
		SeenDimension overworld = new SeenDimension("minecraft:overworld", -64, 384);

		assertEquals(319, overworld.maxY());
		assertTrue(overworld.contains(-64));
		assertTrue(overworld.contains(319));
		assertFalse(overworld.contains(320));
	}
}
