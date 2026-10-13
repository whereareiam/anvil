package me.whereareiam.anvil.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditionTest {
	@Test
	void editionsWithTheSameIdentifierAreEqual() {
		assertEquals(Edition.of("first"), Edition.of("first"));
		assertEquals(Edition.of("first").hashCode(), Edition.of("first").hashCode());
		assertNotEquals(Edition.of("first"), Edition.of("second"));
		assertEquals("first-2", Edition.of("first-2").toString());
	}

	@Test
	void refusesIdentifiersThatAreNotOneLowerCaseWord() {
		for (String invalid : new String[]{"", " ", "First", "2nd", "with space", "-first"})
			assertThrows(IllegalArgumentException.class, () -> Edition.of(invalid), invalid);
	}
}
