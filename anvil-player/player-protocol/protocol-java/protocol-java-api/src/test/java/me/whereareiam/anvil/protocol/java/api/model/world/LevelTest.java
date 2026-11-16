package me.whereareiam.anvil.protocol.java.api.model.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelTest {
	private final Level overworld = new Level("minecraft:overworld", DimensionType.known("minecraft:overworld"));

	@Test
	void spansTheHeightsOfItsDimensionType() {
		assertEquals(-64, overworld.minY());
		assertEquals(24, overworld.sectionCount());
		assertTrue(overworld.contains(-64));
		assertTrue(overworld.contains(319));
		assertFalse(overworld.contains(-65));
		assertFalse(overworld.contains(320));
	}
}
