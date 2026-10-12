package me.whereareiam.anvil.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftEditionTest {
	@Test
	void editionsWithTheSameIdentifierAreEqual() {
		assertEquals(MinecraftEdition.of("first"), MinecraftEdition.of("first"));
		assertEquals(MinecraftEdition.of("first").hashCode(), MinecraftEdition.of("first").hashCode());
		assertNotEquals(MinecraftEdition.of("first"), MinecraftEdition.of("second"));
		assertEquals("first-2", MinecraftEdition.of("first-2").toString());
	}

	@Test
	void refusesIdentifiersThatAreNotOneLowerCaseWord() {
		for (String invalid : new String[]{"", " ", "First", "2nd", "with space", "-first"})
			assertThrows(IllegalArgumentException.class, () -> MinecraftEdition.of(invalid), invalid);
	}
}
