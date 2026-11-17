package me.whereareiam.anvil.protocol.java.physics.geometry;

import org.jetbrains.annotations.NotNull;

/**
 * One axis of the world, as vanilla's {@code Direction.Axis} names them, along which vanilla moves an entity against
 * the blocks around it one axis at a time.
 */
public enum Axis {
	/**
	 * East-west.
	 */
	X,
	/**
	 * Up-down.
	 */
	Y,
	/**
	 * North-south.
	 */
	Z;

	/**
	 * Returns the axis after this one in the cycle X, Y, Z, as {@code AxisCycle.FORWARD} turns them.
	 *
	 * @return the next axis
	 */
	public @NotNull Axis next() {
		return values()[(ordinal() + 1) % 3];
	}
}
