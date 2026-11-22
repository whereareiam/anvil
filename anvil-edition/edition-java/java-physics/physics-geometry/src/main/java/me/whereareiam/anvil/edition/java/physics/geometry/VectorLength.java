package me.whereareiam.anvil.edition.java.physics.geometry;

import org.jetbrains.annotations.NotNull;

/**
 * Vanilla's length and direction of a vector ({@code Vec3.length}, {@code Vec3.normalize}) in each of its forms, so
 * that an entity accelerates along exactly the direction the client of each version computes: before 1.17 the length
 * is a float ({@code Mth.sqrt(double)} returns a float), and before 1.21.2 a vector shorter than {@code 1.0E-4} has no
 * direction, from 1.21.2 one shorter than {@code 1.0E-5F}.
 */
public enum VectorLength {
	/**
	 * The float length of the clients before 1.17.
	 */
	FLOAT(1.0E-4) {
		@Override
		public double length(@NotNull Vector vector) {
			return (float) Math.sqrt(vector.lengthSqr());
		}
	},
	/**
	 * The double length of the clients from 1.17 to 1.21.1.
	 */
	DOUBLE(1.0E-4),
	/**
	 * The double length of the clients from 1.21.2 on, with a direction down to {@code 1.0E-5F}.
	 */
	FINE_DOUBLE(1.0E-5F);

	private final double shortest;

	VectorLength(double shortest) {
		this.shortest = shortest;
	}

	/**
	 * Returns a vector's length ({@code Vec3.length}).
	 *
	 * @param vector the vector
	 * @return its length
	 */
	public double length(@NotNull Vector vector) {
		return Math.sqrt(vector.lengthSqr());
	}

	/**
	 * Returns the vector of length one in a vector's direction, or zero for a vector too short to have one
	 * ({@code Vec3.normalize}).
	 *
	 * @param vector the vector
	 * @return its direction
	 */
	public @NotNull Vector normalize(@NotNull Vector vector) {
		double length = length(vector);
		return length < shortest ? Vector.ZERO : new Vector(vector.x / length, vector.y / length, vector.z / length);
	}
}
