package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.protocol.java.data.model.CollisionBox;
import me.whereareiam.anvil.protocol.java.data.model.CollisionShape;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * The collision shape of a block state as vanilla's {@code VoxelShape} holds it: the coordinates that split the block
 * into cells along each axis and which cells are solid, in block-local coordinates. Vanilla builds each box of a
 * shape on the coarsest grid of eighths that holds its corners ({@code Shapes.create}), so every grid line of such a
 * box is a coordinate of the shape, and a box off that grid adds only its faces; collisions and step heights read
 * these coordinates exactly as vanilla does.
 */
final class BlockShape {
	private final @NotNull List<CollisionBox> boxes;
	private final double @NotNull [][] coordinates;
	private final boolean @NotNull [] solid;
	private final boolean fullBlock;
	private final boolean large;

	private BlockShape(@NotNull List<CollisionBox> boxes, double @NotNull [][] coordinates, boolean @NotNull [] solid) {
		this.boxes = boxes;
		this.coordinates = coordinates;
		this.solid = solid;
		this.fullBlock = boxes.size() == 1 && boxes.getFirst().isFullBlock();
		this.large = boxes.stream().anyMatch(box -> box.getMinX() < 0.0 || box.getMinY() < 0.0 || box.getMinZ() < 0.0
				|| box.getMaxX() > 1.0 || box.getMaxY() > 1.0 || box.getMaxZ() > 1.0);
	}

	static @NotNull BlockShape of(@NotNull CollisionShape shape) {
		List<CollisionBox> boxes = shape.getBoxes();
		double[][] coordinates = new double[3][];
		for (Axis axis : Axis.values()) coordinates[axis.ordinal()] = coordinates(boxes, axis);

		int sizeX = coordinates[0].length - 1;
		int sizeY = coordinates[1].length - 1;
		int sizeZ = coordinates[2].length - 1;
		boolean[] solid = new boolean[Math.max(0, sizeX) * Math.max(0, sizeY) * Math.max(0, sizeZ)];
		for (int x = 0; x < sizeX; x++)
			for (int y = 0; y < sizeY; y++)
				for (int z = 0; z < sizeZ; z++)
					solid[(x * sizeY + y) * sizeZ + z] = inside(boxes,
							(coordinates[0][x] + coordinates[0][x + 1]) / 2.0,
							(coordinates[1][y] + coordinates[1][y + 1]) / 2.0,
							(coordinates[2][z] + coordinates[2][z + 1]) / 2.0);

		return new BlockShape(List.copyOf(boxes), coordinates, solid);
	}

	boolean isEmpty() {
		return boxes.isEmpty();
	}

	boolean isFullBlock() {
		return fullBlock;
	}

	/**
	 * Tells whether the shape reaches beyond its block, as a fence does ({@code hasLargeCollisionShape}).
	 */
	boolean isLarge() {
		return large;
	}

	@NotNull List<CollisionBox> boxes() {
		return boxes;
	}

	/**
	 * The shape's coordinates along an axis in block-local coordinates, ascending, as {@code VoxelShape.getCoords}.
	 */
	double @NotNull [] coordinates(@NotNull Axis axis) {
		return coordinates[axis.ordinal()];
	}

	/**
	 * Tests whether any box of the shape at a block overlaps a box with a volume, as {@code Shapes.joinIsNotEmpty}
	 * with {@code BooleanOp.AND} does.
	 */
	boolean intersects(int blockX, int blockY, int blockZ, @NotNull Box box) {
		for (CollisionBox part : boxes)
			if (box.intersects(part.getMinX() + blockX, part.getMinY() + blockY, part.getMinZ() + blockZ,
					part.getMaxX() + blockX, part.getMaxY() + blockY, part.getMaxZ() + blockZ)) return true;

		return false;
	}

	/**
	 * Limits a movement of a box along one axis by this shape at a block, as {@code VoxelShape.collideX} does: the
	 * nearest solid cell ahead that overlaps the box on the other two axes stops it.
	 *
	 * @param distance the movement, which the shape may only shorten
	 * @return the movement the shape allows
	 */
	double collide(@NotNull Axis axis, int blockX, int blockY, int blockZ, @NotNull Box box, double distance) {
		if (isEmpty()) return distance;
		if (Math.abs(distance) < 1.0E-7) return 0.0;

		Axis first = next(axis);
		Axis second = next(first);
		double[] offsets = {blockX, blockY, blockZ};
		double max = box.max(axis);
		double min = box.min(axis);
		int low = findIndex(axis, offsets, min + 1.0E-7);
		int high = findIndex(axis, offsets, max - 1.0E-7);
		int firstFrom = Math.max(0, findIndex(first, offsets, box.min(first) + 1.0E-7));
		int firstTo = Math.min(size(first), findIndex(first, offsets, box.max(first) - 1.0E-7) + 1);
		int secondFrom = Math.max(0, findIndex(second, offsets, box.min(second) + 1.0E-7));
		int secondTo = Math.min(size(second), findIndex(second, offsets, box.max(second) - 1.0E-7) + 1);
		int size = size(axis);
		if (distance > 0.0) {
			for (int cell = high + 1; cell < size; cell++)
				for (int a = firstFrom; a < firstTo; a++)
					for (int b = secondFrom; b < secondTo; b++) {
						if (!solid(axis, cell, first, a, second, b)) continue;

						double gap = coordinate(axis, offsets, cell) - max;
						if (gap >= -1.0E-7) distance = Math.min(distance, gap);
						return distance;
					}
			return distance;
		}

		for (int cell = low - 1; cell >= 0; cell--)
			for (int a = firstFrom; a < firstTo; a++)
				for (int b = secondFrom; b < secondTo; b++) {
					if (!solid(axis, cell, first, a, second, b)) continue;

					double gap = coordinate(axis, offsets, cell + 1) - min;
					if (gap <= 1.0E-7) distance = Math.max(distance, gap);
					return distance;
				}
		return distance;
	}

	private boolean solid(@NotNull Axis axis, int cell, @NotNull Axis first, int a, @NotNull Axis second, int b) {
		int[] index = new int[3];
		index[axis.ordinal()] = cell;
		index[first.ordinal()] = a;
		index[second.ordinal()] = b;

		return solid[(index[0] * size(Axis.Y) + index[1]) * size(Axis.Z) + index[2]];
	}

	private int size(@NotNull Axis axis) {
		return coordinates[axis.ordinal()].length - 1;
	}

	private double coordinate(@NotNull Axis axis, double @NotNull [] offsets, int index) {
		return coordinates[axis.ordinal()][index] + offsets[axis.ordinal()];
	}

	/**
	 * Finds the cell holding a coordinate, as {@code VoxelShape.findIndex} does with {@code Mth.binarySearch}: the index
	 * before the first coordinate above the value, from {@code -1} to the number of cells.
	 */
	private int findIndex(@NotNull Axis axis, double @NotNull [] offsets, double value) {
		int min = 0;
		int length = size(axis) + 1;
		while (length > 0) {
			int half = length / 2;
			int middle = min + half;
			if (value < coordinate(axis, offsets, middle)) {
				length = half;
			} else {
				min = middle + 1;
				length -= half + 1;
			}
		}

		return min - 1;
	}

	private static @NotNull Axis next(@NotNull Axis axis) {
		return Axis.values()[(axis.ordinal() + 1) % 3];
	}

	private static boolean inside(@NotNull List<CollisionBox> boxes, double x, double y, double z) {
		for (CollisionBox box : boxes)
			if (x >= box.getMinX() && x <= box.getMaxX() && y >= box.getMinY() && y <= box.getMaxY()
					&& z >= box.getMinZ() && z <= box.getMaxZ()) return true;

		return false;
	}

	private static double @NotNull [] coordinates(@NotNull List<CollisionBox> boxes, @NotNull Axis axis) {
		TreeSet<Double> values = new TreeSet<>();
		for (CollisionBox box : boxes) {
			int bits = alignedBits(box, axis);
			if (bits < 0) {
				values.add(min(box, axis));
				values.add(max(box, axis));
				continue;
			}

			int parts = 1 << bits;
			for (int index = 0; index <= parts; index++) values.add((double) index / (double) parts);
		}

		List<Double> sorted = new ArrayList<>(values);
		return sorted.stream().mapToDouble(Double::doubleValue).toArray();
	}

	/**
	 * The grid of eighths vanilla builds a box on along an axis ({@code Shapes.findBits}), or {@code -1} when one of
	 * its axes lies off every such grid and vanilla keeps its exact faces.
	 */
	private static int alignedBits(@NotNull CollisionBox box, @NotNull Axis axis) {
		int[] bits = new int[3];
		for (Axis each : Axis.values()) {
			bits[each.ordinal()] = findBits(min(box, each), max(box, each));
			if (bits[each.ordinal()] < 0) return -1;
		}

		return bits[axis.ordinal()];
	}

	private static int findBits(double min, double max) {
		if (min < -1.0E-7 || max > 1.0000001) return -1;

		for (int bits = 0; bits <= 3; bits++) {
			int parts = 1 << bits;
			double low = min * parts;
			double high = max * parts;
			boolean lowAligned = Math.abs(low - Math.round(low)) < 1.0E-7 * parts;
			boolean highAligned = Math.abs(high - Math.round(high)) < 1.0E-7 * parts;
			if (lowAligned && highAligned) return bits;
		}

		return -1;
	}

	private static double min(@NotNull CollisionBox box, @NotNull Axis axis) {
		return switch (axis) {
			case X -> box.getMinX();
			case Y -> box.getMinY();
			case Z -> box.getMinZ();
		};
	}

	private static double max(@NotNull CollisionBox box, @NotNull Axis axis) {
		return switch (axis) {
			case X -> box.getMaxX();
			case Y -> box.getMaxY();
			case Z -> box.getMaxZ();
		};
	}

	@Override
	public String toString() {
		return "BlockShape" + Arrays.deepToString(coordinates);
	}
}
