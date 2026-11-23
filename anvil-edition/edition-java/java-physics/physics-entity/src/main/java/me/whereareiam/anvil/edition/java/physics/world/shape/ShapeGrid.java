package me.whereareiam.anvil.edition.java.physics.world.shape;

import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.physics.geometry.Axis;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.TreeSet;

/**
 * The cells of a collision shape as vanilla's {@code Shapes.create} and {@code Shapes.or} lay them out: each box on the
 * coarsest grid of eighths that holds its corners ({@code Shapes.findBits}), a box off that grid by its exact faces,
 * and a cell solid where a box covers its middle.
 */
final class ShapeGrid {
	private static final double EPSILON = 1.0E-7;

	private final double @NotNull [][] coordinates;
	private final boolean @NotNull [] solid;

	private ShapeGrid(double @NotNull [][] coordinates, boolean @NotNull [] solid) {
		this.coordinates = coordinates;
		this.solid = solid;
	}

	static @NotNull ShapeGrid of(@NotNull List<CollisionBox> boxes) {
		double[][] coordinates = new double[3][];
		for (Axis axis : Axis.values()) coordinates[axis.ordinal()] = coordinates(boxes, axis);

		int sizeX = Math.max(0, coordinates[0].length - 1);
		int sizeY = Math.max(0, coordinates[1].length - 1);
		int sizeZ = Math.max(0, coordinates[2].length - 1);
		boolean[] solid = new boolean[sizeX * sizeY * sizeZ];
		for (int x = 0; x < sizeX; x++)
			for (int y = 0; y < sizeY; y++)
				for (int z = 0; z < sizeZ; z++)
					solid[(x * sizeY + y) * sizeZ + z] = inside(boxes, middle(coordinates[0], x),
							middle(coordinates[1], y), middle(coordinates[2], z));

		return new ShapeGrid(coordinates, solid);
	}

	double @NotNull [][] coordinates() {
		return coordinates;
	}

	boolean @NotNull [] solid() {
		return solid;
	}

	private static double middle(double @NotNull [] coordinates, int cell) {
		return (coordinates[cell] + coordinates[cell + 1]) / 2.0;
	}

	private static boolean inside(@NotNull List<CollisionBox> boxes, double x, double y, double z) {
		for (CollisionBox box : boxes)
			if (x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY()
					&& z >= box.minZ() && z <= box.maxZ()) return true;

		return false;
	}

	private static double @NotNull [] coordinates(@NotNull List<CollisionBox> boxes, @NotNull Axis axis) {
		TreeSet<Double> values = new TreeSet<>();
		for (CollisionBox box : boxes) {
			int bits = alignedBits(box, axis);
			if (bits < 0) {
				values.add(bound(box, axis, false));
				values.add(bound(box, axis, true));
				continue;
			}

			int parts = 1 << bits;
			for (int index = 0; index <= parts; index++) values.add((double) index / (double) parts);
		}

		return values.stream().mapToDouble(Double::doubleValue).toArray();
	}

	/**
	 * The grid of eighths vanilla builds a box on along an axis ({@code Shapes.findBits}), or {@code -1} when one of its
	 * axes lies off every such grid and vanilla keeps its exact faces.
	 */
	private static int alignedBits(@NotNull CollisionBox box, @NotNull Axis axis) {
		for (Axis each : Axis.values())
			if (findBits(bound(box, each, false), bound(box, each, true)) < 0) return -1;

		return findBits(bound(box, axis, false), bound(box, axis, true));
	}

	private static int findBits(double min, double max) {
		if (min < -EPSILON || max > 1.0000001) return -1;

		for (int bits = 0; bits <= 3; bits++) {
			int parts = 1 << bits;
			double low = min * parts;
			double high = max * parts;
			if (Math.abs(low - Math.round(low)) < EPSILON * parts && Math.abs(high - Math.round(high)) < EPSILON * parts)
				return bits;
		}

		return -1;
	}

	private static double bound(@NotNull CollisionBox box, @NotNull Axis axis, boolean upper) {
		return switch (axis) {
			case X -> upper ? box.maxX() : box.minX();
			case Y -> upper ? box.maxY() : box.minY();
			case Z -> upper ? box.maxZ() : box.minZ();
		};
	}
}
