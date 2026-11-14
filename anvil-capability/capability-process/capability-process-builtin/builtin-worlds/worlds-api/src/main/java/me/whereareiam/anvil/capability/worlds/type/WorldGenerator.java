package me.whereareiam.anvil.capability.worlds.type;

/**
 * How a world a test creates generates its terrain.
 */
public enum WorldGenerator {
	/**
	 * The normal terrain of the world's kind, as the server generates it for a new world of that kind.
	 */
	DEFAULT,
	/**
	 * A superflat world, with the server's default layers.
	 */
	FLAT,
	/**
	 * A world without any blocks, whose spawn is the block at {@code 0, 64, 0}: a player sent there falls, so a test
	 * places what it needs first.
	 */
	EMPTY
}
