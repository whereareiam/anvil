package me.whereareiam.anvil.capability.world.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeenChunkTest {
	@Test
	void placesBlocksInTheirChunkColumns() {
		assertEquals(new SeenChunk(-1, 2), SeenChunk.containing(-1, 47));
		assertTrue(new SeenChunk(-1, 2).contains(-16, 32));
		assertFalse(new SeenChunk(-1, 2).contains(0, 32));
	}
}
