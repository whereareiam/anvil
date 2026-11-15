package me.whereareiam.anvil.protocol.java.block.generator.facts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VanillaNumberTest {
	@Test
	void widensAFloatAsJavaDoes() {
		VanillaNumber bed = VanillaNumber.parse("0.66F");

		assertEquals(0.66F, bed.asFloat("bounce"));
		assertEquals((double) 0.66F, bed.asDouble());
		assertEquals(0.25, VanillaNumber.parse("0.25D").asDouble());
	}

	@Test
	void refusesADoubleWhereVanillaHasAFloat() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> VanillaNumber.parse("0.98D").asFloat("friction"));

		assertEquals("friction is a float in vanilla, not 0.98D", failure.getMessage());
	}

	@Test
	void refusesTextThatIsNoLiteral() {
		assertThrows(IllegalArgumentException.class, () -> VanillaNumber.parse("0.98"));
		assertThrows(IllegalArgumentException.class, () -> VanillaNumber.parse("fast"));
	}
}
