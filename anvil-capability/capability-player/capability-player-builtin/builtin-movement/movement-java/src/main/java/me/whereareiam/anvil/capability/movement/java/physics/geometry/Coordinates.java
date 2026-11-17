package me.whereareiam.anvil.capability.movement.java.physics.geometry;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Block coordinates of world coordinates, rounded as vanilla's {@code Mth.floor} and {@code Mth.ceil} round them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Coordinates {
	/**
	 * Rounds down as {@code Mth.floor} does.
	 *
	 * @param value the value
	 * @return the largest integer not above it
	 */
	public static int floor(double value) {
		int truncated = (int) value;
		return value < truncated ? truncated - 1 : truncated;
	}

	/**
	 * Rounds up as {@code Mth.ceil} does.
	 *
	 * @param value the value
	 * @return the smallest integer not below it
	 */
	public static int ceil(double value) {
		int truncated = (int) value;
		return value > truncated ? truncated + 1 : truncated;
	}
}
