package me.whereareiam.anvil.testkit.physics.harness;

import java.util.Locale;

/**
 * A key a course can hold, each bound to vanilla's key mapping of the same purpose.
 */
public enum CourseKey {
	FORWARD,
	BACKWARD,
	LEFT,
	RIGHT,
	JUMP,
	SNEAK,
	SPRINT;

	/**
	 * Returns the key a course names, such as {@code forward}.
	 *
	 * @param name the key's name in a course
	 * @return the key
	 * @throws IllegalArgumentException when no key has that name
	 */
	public static CourseKey named(String name) {
		for (CourseKey key : values())
			if (key.courseName().equals(name))
				return key;
		throw new IllegalArgumentException("'" + name + "' is no key; expected one of forward, backward, left, right, jump, sneak, sprint");
	}

	/**
	 * The key's name in a course.
	 */
	public String courseName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
