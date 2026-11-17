package me.whereareiam.anvil.capability.packets.recording;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityPairingTest {
	private final EntityPairing entities = new EntityPairing();

	@Test
	void pairsTheIdsEachStreamNamesFirstAndKeepsThemPaired() {
		assertNull(entities.pair(42, 57));
		assertNull(entities.pair(43, 58));
		assertNull(entities.pair(42, 57));
	}

	@Test
	void refusesAnIdOfOneStreamNamedWithAnotherOfTheOther() {
		entities.pair(42, 57);

		assertEquals("expected entity 42 is entity 57 of the actual stream, which names 58 here", entities.pair(42, 58));
		assertEquals("actual entity 57 is entity 42 of the expected stream, which names 43 here", entities.pair(43, 57));
	}
}
