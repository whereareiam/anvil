package me.whereareiam.anvil.protocol.java.data.model;

import lombok.Value;

/**
 * One axis-aligned box of a block's collision shape, in block-local coordinates: {@code 0} is the block's north-west
 * bottom corner and {@code 1} its far side, so a full block is {@code 0..1} on every axis.
 *
 * <p>A box may reach beyond the block, as a fence's reaches {@code 1.5} high. Vanilla moves an entity against the
 * boxes of the blocks around it one axis at a time, after offsetting each box by its block's position.</p>
 */
@Value
public class CollisionBox {
	/**
	 * The west face, the lowest east-west coordinate.
	 */
	double minX;
	/**
	 * The bottom face.
	 */
	double minY;
	/**
	 * The north face, the lowest north-south coordinate.
	 */
	double minZ;
	/**
	 * The east face, the highest east-west coordinate.
	 */
	double maxX;
	/**
	 * The top face.
	 */
	double maxY;
	/**
	 * The south face, the highest north-south coordinate.
	 */
	double maxZ;

	/**
	 * Tests whether this box fills the whole block, {@code 0..1} on every axis.
	 *
	 * @return whether the box is the full block
	 */
	public boolean isFullBlock() {
		return minX == 0 && minY == 0 && minZ == 0 && maxX == 1 && maxY == 1 && maxZ == 1;
	}
}
