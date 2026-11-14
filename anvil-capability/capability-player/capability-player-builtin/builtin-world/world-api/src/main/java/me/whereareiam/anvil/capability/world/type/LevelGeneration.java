package me.whereareiam.anvil.capability.world.type;

/**
 * How the server generates a level, as far as the client is told: the client lowers its horizon over a superflat
 * world and shows a debug world's block states.
 */
public enum LevelGeneration {
	/**
	 * Any generation but superflat and debug, such as the default terrain, amplified or a data pack's.
	 */
	NORMAL,
	/**
	 * A superflat world.
	 */
	FLAT,
	/**
	 * A debug world, which shows every block state and cannot be changed.
	 */
	DEBUG
}
