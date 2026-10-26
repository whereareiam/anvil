package me.whereareiam.anvil.capability.movement.java.physics;

/**
 * Vanilla's table sine and cosine ({@code Mth.sin}, {@code Mth.cos}) in both of its forms, so that movement turns by
 * exactly the values the client of each version computes.
 */
final class Trigonometry {
	private static final float[] FLOAT_TABLE = new float[65536];
	private static final float[] DOUBLE_TABLE = new float[65536];

	static {
		for (int index = 0; index < FLOAT_TABLE.length; index++) {
			FLOAT_TABLE[index] = (float) Math.sin((double) index * Math.PI * 2.0 / 65536.0);
			DOUBLE_TABLE[index] = (float) Math.sin(index / 10430.378350470453);
		}
	}

	private final boolean doublePrecision;

	Trigonometry(boolean doublePrecision) {
		this.doublePrecision = doublePrecision;
	}

	float sin(float radians) {
		if (doublePrecision) return DOUBLE_TABLE[(int) ((long) (radians * 10430.378350470453) & 65535L)];

		return FLOAT_TABLE[(int) (radians * 10430.378F) & 65535];
	}

	float cos(float radians) {
		if (doublePrecision) return DOUBLE_TABLE[(int) ((long) (radians * 10430.378350470453 + 16384.0) & 65535L)];

		return FLOAT_TABLE[(int) (radians * 10430.378F + 16384.0F) & 65535];
	}
}
