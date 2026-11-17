package me.whereareiam.anvil.protocol.java.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the length and direction of vectors in each of the vanilla client's forms.
 */
class VectorLengthTest {
	@Test
	void measuresAFloatLengthBefore1_17() {
		Vector diagonal = new Vector(0.98, 0.0, 0.98);

		assertEquals((float) Math.sqrt(0.98 * 0.98 + 0.98 * 0.98), VectorLength.FLOAT.length(diagonal));
		assertEquals(0.98 / (float) Math.sqrt(0.98 * 0.98 + 0.98 * 0.98), VectorLength.FLOAT.normalize(diagonal).x);
		assertEquals(0.98 / Math.sqrt(0.98 * 0.98 + 0.98 * 0.98), VectorLength.DOUBLE.normalize(diagonal).x);
	}

	@Test
	void givesShortVectorsADirectionDownTo1E_5FromThe1_21_2Form() {
		Vector tiny = new Vector(5.0E-5, 0.0, 0.0);

		assertEquals(Vector.ZERO, VectorLength.DOUBLE.normalize(tiny));
		assertEquals(1.0, VectorLength.FINE_DOUBLE.normalize(tiny).x);
	}
}
