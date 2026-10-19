package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Moves boxes against the collision shapes of the blocks the client knows, as vanilla's {@code BlockCollisions},
 * {@code Shapes.collide} and {@code Entity.collideWithShapes} do. Entity collisions, such as boats, and the world
 * border are not part of the world the client knows here.
 */
final class Collisions {
	private static final String MOVING_PISTON = "minecraft:moving_piston";

	private final @NotNull BlockView view;

	Collisions(@NotNull BlockView view) {
		this.view = view;
	}

	/**
	 * Collects the shapes of the blocks that overlap a box, in vanilla's order: east-west fastest, then up, then
	 * north-south, over the blocks around the box, leaving out the blocks of the outer layer unless their shape reaches
	 * beyond them.
	 */
	@NotNull List<Placed> blockCollisions(@NotNull Box box) {
		int minX = Fluids.floor(box.minX - 1.0E-7) - 1;
		int maxX = Fluids.floor(box.maxX + 1.0E-7) + 1;
		int minY = Fluids.floor(box.minY - 1.0E-7) - 1;
		int maxY = Fluids.floor(box.maxY + 1.0E-7) + 1;
		int minZ = Fluids.floor(box.minZ - 1.0E-7) - 1;
		int maxZ = Fluids.floor(box.maxZ + 1.0E-7) + 1;
		List<Placed> shapes = new ArrayList<>();
		for (int z = minZ; z <= maxZ; z++)
			for (int y = minY; y <= maxY; y++)
				for (int x = minX; x <= maxX; x++) {
					int edges = (x == minX || x == maxX ? 1 : 0) + (y == minY || y == maxY ? 1 : 0) + (z == minZ || z == maxZ ? 1 : 0);
					if (edges == 3) continue;

					Placed placed = placed(x, y, z, edges, box);
					if (placed != null) shapes.add(placed);
				}

		return shapes;
	}

	private @Nullable Placed placed(int x, int y, int z, int edges, @NotNull Box box) {
		BlockState state = view.state(x, y, z);
		BlockShape shape = view.shape(state);
		if (shape.isEmpty()) return null;
		if (edges == 1 && !shape.isLarge()) return null;
		if (edges == 2 && !state.getBlock().getName().equals(MOVING_PISTON)) return null;
		if (shape.isFullBlock() && !box.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) return null;
		if (!shape.isFullBlock() && !shape.intersects(x, y, z, box)) return null;

		return new Placed(shape, x, y, z);
	}

	/**
	 * Tells whether no block overlaps a box ({@code noCollision}).
	 */
	boolean noCollision(@NotNull Box box) {
		return blockCollisions(box).isEmpty();
	}

	/**
	 * Limits a movement along one axis by several shapes, as {@code Shapes.collide} does: a movement shorter than
	 * {@code 1.0E-7} before any shape is none.
	 */
	static double collide(@NotNull Axis axis, @NotNull Box box, @NotNull List<Placed> shapes, double distance) {
		for (Placed shape : shapes) {
			if (Math.abs(distance) < 1.0E-7) return 0.0;

			distance = shape.shape().collide(axis, shape.x(), shape.y(), shape.z(), box, distance);
		}

		return distance;
	}

	/**
	 * Moves a box against shapes one axis at a time, vertically first and then along the longer horizontal axis
	 * ({@code collideWithShapes}).
	 */
	static @NotNull Vector collideWithShapes(@NotNull Vector movement, @NotNull Box box, @NotNull List<Placed> shapes) {
		if (shapes.isEmpty()) return movement;

		Vector result = Vector.ZERO;
		Axis[] order = Math.abs(movement.x) < Math.abs(movement.z)
				? new Axis[]{Axis.Y, Axis.Z, Axis.X}
				: new Axis[]{Axis.Y, Axis.X, Axis.Z};
		for (Axis axis : order) {
			double distance = movement.get(axis);
			if (distance == 0.0) continue;

			result = result.with(axis, collide(axis, box.move(result), shapes, distance));
		}

		return result;
	}

	/**
	 * Moves a box against the blocks its movement passes ({@code collideBoundingBox}).
	 */
	@NotNull Vector collideBoundingBox(@NotNull Vector movement, @NotNull Box box) {
		return collideWithShapes(movement, box, blockCollisions(box.expandTowards(movement)));
	}

	/**
	 * The heights a box at the bottom of {@code base} could step up to among shapes, ascending, as
	 * {@code collectCandidateStepUpHeights}: every coordinate of the shapes between the box's bottom and the step
	 * height, except the height the collided movement already reached.
	 */
	static float @NotNull [] candidateStepHeights(@NotNull Box base, @NotNull List<Placed> shapes, float stepHeight, float reached) {
		TreeSet<Float> heights = new TreeSet<>();
		for (Placed shape : shapes)
			for (double coordinate : shape.shape().coordinates(Axis.Y)) {
				float height = (float) (coordinate + shape.y() - base.minY);
				if (height < 0.0F || height == reached) continue;
				if (height > stepHeight) break;

				heights.add(height);
			}

		float[] sorted = new float[heights.size()];
		int index = 0;
		for (float height : heights) sorted[index++] = height;

		return sorted;
	}

	/**
	 * Finds the block an entity stands on among those below a box: the one whose centre is closest to the entity's
	 * position, the higher, more southern and more eastern one among equally close ones ({@code findSupportingBlock}).
	 *
	 * @return the block's x, y and z, or {@code null} when no block is below
	 */
	int @Nullable [] supportingBlock(@NotNull Box box, double x, double y, double z) {
		int[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Placed shape : blockCollisions(box)) {
			double dx = shape.x() + 0.5 - x;
			double dy = shape.y() + 0.5 - y;
			double dz = shape.z() + 0.5 - z;
			double distance = dx * dx + dy * dy + dz * dz;
			int[] position = {shape.x(), shape.y(), shape.z()};
			if (distance < bestDistance || distance == bestDistance && (best == null || compare(best, position) < 0)) {
				best = position;
				bestDistance = distance;
			}
		}

		return best;
	}

	/**
	 * Orders block positions as {@code Vec3i.compareTo}: by height, then north-south, then east-west.
	 */
	private static int compare(int @NotNull [] first, int @NotNull [] second) {
		if (first[1] != second[1]) return first[1] - second[1];
		if (first[2] != second[2]) return first[2] - second[2];

		return first[0] - second[0];
	}

	/**
	 * A block's collision shape at the block's position.
	 */
	record Placed(@NotNull BlockShape shape, int x, int y, int z) {
	}
}
