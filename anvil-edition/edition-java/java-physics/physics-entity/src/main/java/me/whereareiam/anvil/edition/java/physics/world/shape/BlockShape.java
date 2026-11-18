package me.whereareiam.anvil.edition.java.physics.world.shape;

import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionShape;
import me.whereareiam.anvil.edition.java.physics.geometry.Axis;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The collision shape of a block state as vanilla's {@code VoxelShape} holds it: the coordinates that split the block
 * into cells along each axis and which cells are solid, in block-local coordinates. Vanilla builds each box of a shape
 * on the coarsest grid of eighths that holds its corners ({@code Shapes.create}), so every grid line of such a box is a
 * coordinate of the shape, and a box off that grid adds only its faces; collisions and step heights read these
 * coordinates exactly as vanilla does.
 */
public final class BlockShape {
	private static final double EPSILON = 1.0E-7;

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

	/**
	 * Builds the voxel shape of a collision shape.
	 *
	 * @param shape the collision shape the block data records
	 * @return the voxel shape
	 */
	public static @NotNull BlockShape of(@NotNull CollisionShape shape) {
		List<CollisionBox> boxes = List.copyOf(shape.getBoxes());
		ShapeGrid grid = ShapeGrid.of(boxes);
		return new BlockShape(boxes, grid.coordinates(), grid.solid());
	}

	/**
	 * Tells whether the shape has no box.
	 *
	 * @return whether entities move through it
	 */
	public boolean isEmpty() {
		return boxes.isEmpty();
	}

	/**
	 * Tells whether the shape is exactly the full block, {@code Shapes.block()}.
	 *
	 * @return whether it fills the block
	 */
	public boolean isFullBlock() {
		return fullBlock;
	}

	/**
	 * Tells whether the shape reaches beyond its block, as a fence does ({@code hasLargeCollisionShape}).
	 *
	 * @return whether it reaches out of the block
	 */
	public boolean isLarge() {
		return large;
	}

	/**
	 * Returns the shape's boxes in block-local coordinates.
	 *
	 * @return the boxes
	 */
	public @NotNull List<CollisionBox> boxes() {
		return boxes;
	}

	/**
	 * Returns the shape's coordinates along an axis in block-local coordinates, ascending, as
	 * {@code VoxelShape.getCoords} does.
	 *
	 * @param axis the axis
	 * @return the coordinates
	 */
	public double @NotNull [] coordinates(@NotNull Axis axis) {
		return coordinates[axis.ordinal()];
	}

	/**
	 * Tests whether any box of the shape at a block overlaps a box with a volume, as {@code Shapes.joinIsNotEmpty} with
	 * {@code BooleanOp.AND} does.
	 *
	 * @param block the block's position
	 * @param box the box
	 * @return whether they overlap
	 */
	public boolean intersects(int @NotNull [] block, @NotNull Box box) {
		for (CollisionBox part : boxes)
			if (box.intersects(new Box(
					new Vector(part.getMinX() + block[0], part.getMinY() + block[1], part.getMinZ() + block[2]),
					new Vector(part.getMaxX() + block[0], part.getMaxY() + block[1], part.getMaxZ() + block[2])
			))) return true;

		return false;
	}

	/**
	 * Limits a movement of a box along one axis by this shape at a block, as {@code VoxelShape.collideX} does: the
	 * nearest solid cell ahead that overlaps the box on the other two axes stops it.
	 *
	 * @param axis the movement's axis
	 * @param block the block's position
	 * @param box the moving box
	 * @param distance the movement, which the shape may only shorten
	 * @return the movement the shape allows
	 */
	public double collide(@NotNull Axis axis, int @NotNull [] block, @NotNull Box box, double distance) {
		if (isEmpty()) return distance;
		if (Math.abs(distance) < EPSILON) return 0.0;

		Axis first = axis.next();
		Axis second = first.next();
		int[] across = {
				Math.max(0, findIndex(first, block, box.min(first) + EPSILON)),
				Math.min(size(first), findIndex(first, block, box.max(first) - EPSILON) + 1),
				Math.max(0, findIndex(second, block, box.min(second) + EPSILON)),
				Math.min(size(second), findIndex(second, block, box.max(second) - EPSILON) + 1)
		};
		if (distance > 0.0) {
			int cell = solidCell(axis, findIndex(axis, block, box.max(axis) - EPSILON) + 1, 1, across);
			if (cell < 0) return distance;

			double gap = coordinate(axis, block, cell) - box.max(axis);
			return gap >= -EPSILON ? Math.min(distance, gap) : distance;
		}

		int cell = solidCell(axis, findIndex(axis, block, box.min(axis) + EPSILON) - 1, -1, across);
		if (cell < 0) return distance;

		double gap = coordinate(axis, block, cell + 1) - box.min(axis);
		return gap <= EPSILON ? Math.max(distance, gap) : distance;
	}

	/**
	 * Finds the first cell along an axis, from a cell on in a direction, that is solid somewhere across the box.
	 *
	 * @return the cell, or {@code -1} when none is
	 */
	private int solidCell(@NotNull Axis axis, int from, int step, int @NotNull [] across) {
		for (int cell = from; cell >= 0 && cell < size(axis); cell += step)
			if (solidAcross(axis, cell, across)) return cell;

		return -1;
	}

	private boolean solidAcross(@NotNull Axis axis, int cell, int @NotNull [] across) {
		Axis first = axis.next();
		Axis second = first.next();
		int[] index = new int[3];
		index[axis.ordinal()] = cell;
		for (int a = across[0]; a < across[1]; a++)
			for (int b = across[2]; b < across[3]; b++) {
				index[first.ordinal()] = a;
				index[second.ordinal()] = b;
				if (solid[(index[0] * size(Axis.Y) + index[1]) * size(Axis.Z) + index[2]]) return true;
			}

		return false;
	}

	private int size(@NotNull Axis axis) {
		return coordinates[axis.ordinal()].length - 1;
	}

	private double coordinate(@NotNull Axis axis, int @NotNull [] block, int index) {
		return coordinates[axis.ordinal()][index] + block[axis.ordinal()];
	}

	/**
	 * Finds the cell holding a coordinate, as {@code VoxelShape.findIndex} does with {@code Mth.binarySearch}: the index
	 * before the first coordinate above the value, from {@code -1} to the number of cells.
	 */
	private int findIndex(@NotNull Axis axis, int @NotNull [] block, double value) {
		int min = 0;
		int length = size(axis) + 1;
		while (length > 0) {
			int half = length / 2;
			int middle = min + half;
			if (value < coordinate(axis, block, middle)) {
				length = half;
				continue;
			}

			min = middle + 1;
			length -= half + 1;
		}

		return min - 1;
	}
}
