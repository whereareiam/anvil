package me.whereareiam.anvil.protocol.java.physics.world.shape;

import me.whereareiam.anvil.protocol.java.geometry.Box;
import me.whereareiam.anvil.protocol.java.geometry.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the solid cells of a shape into boxes as vanilla's {@code VoxelShape.toAabbs} does through
 * {@code DiscreteVoxelShape.forAllBoxes} with combining: each run of solid cells along north-south is widened east and
 * then up as far as whole runs continue, and taken out of the cells. The cells are walked east-west first, then up,
 * before 1.18, and up first from 1.18 ({@code BitSetDiscreteVoxelShape.forAllBoxes}), which orders the boxes
 * differently.
 */
final class ShapeBoxes {
	private final double @NotNull [][] coordinates;
	private final boolean @NotNull [] solid;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final int @NotNull [] block;
	private final @NotNull List<Box> boxes = new ArrayList<>();

	private ShapeBoxes(double @NotNull [][] coordinates, boolean @NotNull [] solid, int @NotNull [] block) {
		this.coordinates = coordinates;
		this.solid = solid.clone();
		this.sizeX = coordinates[0].length - 1;
		this.sizeY = coordinates[1].length - 1;
		this.sizeZ = coordinates[2].length - 1;
		this.block = block;
	}

	/**
	 * Returns the boxes of a shape at a block, in vanilla's order.
	 *
	 * @param coordinates the shape's coordinates along each axis, in block-local coordinates
	 * @param solid which cells are solid, indexed east-west, then up, then north-south fastest
	 * @param block the block's position
	 * @param heightFirst whether the cells are walked up first, as from 1.18
	 * @return the boxes at the block
	 */
	static @NotNull List<Box> of(
			double @NotNull [][] coordinates,
			boolean @NotNull [] solid,
			int @NotNull [] block,
			boolean heightFirst
	) {
		ShapeBoxes shape = new ShapeBoxes(coordinates, solid, block);
		int outer = heightFirst ? shape.sizeY : shape.sizeX;
		int inner = heightFirst ? shape.sizeX : shape.sizeY;
		for (int first = 0; first < outer; first++)
			for (int second = 0; second < inner; second++)
				shape.strips(heightFirst ? second : first, heightFirst ? first : second);
		return shape.boxes;
	}

	/**
	 * Takes a box out of each run of solid cells along north-south at one column of cells.
	 */
	private void strips(int x, int y) {
		int start = -1;
		for (int z = 0; z <= sizeZ; z++) {
			if (full(x, y, z)) {
				if (start == -1) start = z;
				continue;
			}
			if (start == -1) continue;

			take(x, y, start, z);
			start = -1;
		}
	}

	/**
	 * Takes the run from {@code fromZ} to before {@code toZ}, widened east and then up as far as whole runs continue.
	 */
	private void take(int x, int y, int fromZ, int toZ) {
		int toX = x;
		int toY = y;
		clear(fromZ, toZ, x, y);
		while (stripFull(fromZ, toZ, toX + 1, y)) clear(fromZ, toZ, ++toX, y);
		while (rectangleFull(x, toX + 1, fromZ, toZ, toY + 1)) {
			for (int column = x; column <= toX; column++) clear(fromZ, toZ, column, toY + 1);
			toY++;
		}
		boxes.add(new Box(
				new Vector(coordinate(0, x), coordinate(1, y), coordinate(2, fromZ)),
				new Vector(coordinate(0, toX + 1), coordinate(1, toY + 1), coordinate(2, toZ))
		));
	}

	private double coordinate(int axis, int index) {
		return coordinates[axis][index] + block[axis];
	}

	private boolean full(int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ && solid[index(x, y, z)];
	}

	private boolean stripFull(int fromZ, int toZ, int x, int y) {
		if (x >= sizeX || y >= sizeY) return false;

		for (int z = fromZ; z < toZ; z++)
			if (!solid[index(x, y, z)]) return false;
		return true;
	}

	private boolean rectangleFull(int fromX, int toX, int fromZ, int toZ, int y) {
		for (int x = fromX; x < toX; x++)
			if (!stripFull(fromZ, toZ, x, y)) return false;
		return true;
	}

	private void clear(int fromZ, int toZ, int x, int y) {
		for (int z = fromZ; z < toZ; z++) solid[index(x, y, z)] = false;
	}

	private int index(int x, int y, int z) {
		return (x * sizeY + y) * sizeZ + z;
	}
}
