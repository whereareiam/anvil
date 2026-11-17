package me.whereareiam.anvil.testkit.physics.harness;

import java.util.Locale;

/**
 * What a teleport of a course gives relative to the player's current value, named as Anvil's protocol names it.
 */
public enum CourseRelative {
	X(false),
	Y(false),
	Z(false),
	YAW(false),
	PITCH(false),
	DELTA_X(true),
	DELTA_Y(true),
	DELTA_Z(true),
	ROTATE_DELTA(true);

	private final boolean velocity;

	CourseRelative(boolean velocity) {
		this.velocity = velocity;
	}

	/**
	 * Returns the relative a course names, such as {@code delta_y}.
	 *
	 * @param name the name in a course
	 * @return the relative
	 * @throws IllegalArgumentException when no relative has that name
	 */
	public static CourseRelative named(String name) {
		for (CourseRelative relative : values())
			if (relative.name().toLowerCase(Locale.ROOT).equals(name))
				return relative;
		throw new IllegalArgumentException("'" + name + "' is no relative; expected one of x, y, z, yaw, pitch, delta_x, "
			+ "delta_y, delta_z, rotate_delta");
	}

	/**
	 * Whether it concerns the velocity, which only releases from Minecraft 1.21.2 carry in a teleport.
	 */
	public boolean isVelocity() {
		return velocity;
	}
}
