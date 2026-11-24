package me.whereareiam.anvil.testkit.physics.harness;

import java.util.Locale;

/**
 * What the server of a course animates of the player's own entity, named as Anvil's protocol names it, with the action
 * the game's animate packet carries for it.
 */
public enum CourseAnimation {
	SWING_MAIN_HAND(0),
	SWING_OFF_HAND(3);

	private final int action;

	CourseAnimation(int action) {
		this.action = action;
	}

	/**
	 * Returns the animation a course names, such as {@code swing_main_hand}.
	 *
	 * @param name the name in a course
	 * @return the animation
	 * @throws IllegalArgumentException when no animation has that name
	 */
	public static CourseAnimation named(String name) {
		for (CourseAnimation animation : values())
			if (animation.name().toLowerCase(Locale.ROOT).equals(name))
				return animation;
		throw new IllegalArgumentException("'" + name + "' is no animation; expected swing_main_hand or swing_off_hand");
	}

	/**
	 * Returns the action of the game's animate packet, which every release numbers alike.
	 *
	 * @return the action
	 */
	public int action() {
		return action;
	}
}
