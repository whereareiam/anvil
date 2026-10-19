package me.whereareiam.anvil.protocol.java.data.type;

/**
 * The fluid a block state holds, which decides whether an entity in it swims or burns.
 */
public enum FluidType {
	/**
	 * No fluid.
	 */
	NONE,
	/**
	 * Water: a water block, a block that always holds water, such as kelp or a bubble column, or a waterlogged state.
	 */
	WATER,
	/**
	 * Lava.
	 */
	LAVA
}
