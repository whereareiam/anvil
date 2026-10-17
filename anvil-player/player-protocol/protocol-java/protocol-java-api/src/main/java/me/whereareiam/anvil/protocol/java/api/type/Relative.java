package me.whereareiam.anvil.protocol.java.api.type;

/**
 * A coordinate or angle of a teleport that is added to the current one instead of replacing it.
 */
public enum Relative {
	/**
	 * The east-west coordinate.
	 */
	X,
	/**
	 * The height.
	 */
	Y,
	/**
	 * The north-south coordinate.
	 */
	Z,
	/**
	 * The horizontal direction.
	 */
	YAW,
	/**
	 * The vertical direction.
	 */
	PITCH
}
