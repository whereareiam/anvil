package me.whereareiam.anvil.testkit.physics.harness.block;

import lombok.Value;

/**
 * One box of a collision shape as {@code VoxelShape.toAabbs} gives it, in block-local coordinates.
 */
@Value
public class ShapeBox {
	double minX;
	double minY;
	double minZ;
	double maxX;
	double maxY;
	double maxZ;

	/**
	 * Returns this box moved by an offset, as {@code VoxelShape.move} adds it to each coordinate.
	 */
	public ShapeBox move(double x, double y, double z) {
		return new ShapeBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
	}
}
