package me.whereareiam.anvil.protocol.java.api.type;

/**
 * A coordinate, angle or velocity of a teleport that is added to the current one instead of replacing it.
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
	PITCH,
	/**
	 * The east-west velocity.
	 */
	DELTA_X,
	/**
	 * The vertical velocity.
	 */
	DELTA_Y,
	/**
	 * The north-south velocity.
	 */
	DELTA_Z,
	/**
	 * The current velocity turns by the change of the view direction before the teleport's velocity is added to it.
	 */
	ROTATE_DELTA
}
