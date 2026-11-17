package me.whereareiam.anvil.protocol.java.physics.geometry;

/**
 * Vanilla's table sine and cosine ({@code Mth.sin}, {@code Mth.cos}) in both of its forms, so that an entity turns by
 * exactly the values the client of each version computes: before 1.21.2 they take a float and index a table of
 * {@code i * 2 * PI / 65536}, from 1.21.2 they take a double and index a table built from
 * {@code i / 10430.378350470453}.
 */
public enum Trigonometry {
	/**
	 * The float form of the clients before 1.21.2.
	 */
	FLOAT {
		@Override
		public float sin(float radians) {
			return Tables.FLOAT[(int) (radians * 10430.378F) & 65535];
		}

		@Override
		public float cos(float radians) {
			return Tables.FLOAT[(int) (radians * 10430.378F + 16384.0F) & 65535];
		}
	},
	/**
	 * The double form of the clients from 1.21.2 on.
	 */
	DOUBLE {
		@Override
		public float sin(float radians) {
			return Tables.DOUBLE[(int) ((long) (radians * 10430.378350470453) & 65535L)];
		}

		@Override
		public float cos(float radians) {
			return Tables.DOUBLE[(int) ((long) (radians * 10430.378350470453 + 16384.0) & 65535L)];
		}
	};

	/**
	 * The sine of an angle as the client's table gives it.
	 *
	 * @param radians the angle
	 * @return the sine
	 */
	public abstract float sin(float radians);

	/**
	 * The cosine of an angle as the client's table gives it.
	 *
	 * @param radians the angle
	 * @return the cosine
	 */
	public abstract float cos(float radians);

	/**
	 * The two sine tables, built once on first use.
	 */
	private static final class Tables {
		private static final float[] FLOAT = new float[65536];
		private static final float[] DOUBLE = new float[65536];

		static {
			for (int index = 0; index < FLOAT.length; index++) {
				FLOAT[index] = (float) Math.sin(index * Math.PI * 2.0 / 65536.0);
				DOUBLE[index] = (float) Math.sin(index / 10430.378350470453);
			}
		}
	}
}
