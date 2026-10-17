package me.whereareiam.anvil.protocol.java.api.type;

/**
 * The six directions along the world's axes, named as the game's directions; a block interaction names the face it
 * targets with one.
 */
public enum Direction {
	/**
	 * Towards negative Y, the bottom face.
	 */
	DOWN,
	/**
	 * Towards positive Y, the top face.
	 */
	UP,
	/**
	 * Towards negative Z.
	 */
	NORTH,
	/**
	 * Towards positive Z.
	 */
	SOUTH,
	/**
	 * Towards negative X.
	 */
	WEST,
	/**
	 * Towards positive X.
	 */
	EAST
}
