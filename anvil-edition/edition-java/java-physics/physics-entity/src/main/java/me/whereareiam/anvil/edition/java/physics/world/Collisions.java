package me.whereareiam.anvil.edition.java.physics.world;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionShape;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.physics.geometry.Axis;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Coordinates;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.shape.BlockShape;
import me.whereareiam.anvil.edition.java.physics.world.shape.PlacedShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Moves boxes against the collision shapes of the blocks the client knows, as vanilla's {@code BlockCollisions},
 * {@code Shapes.collide} and {@code Entity.collideWithShapes} do for an entity. Scaffolding and powder snow get the
 * shape their {@code getCollisionShape} gives the entity ({@link EntityContext}); entity collisions, such as boats, and
 * the world border are not part of the world the client knows here.
 */
public final class Collisions {
	private static final double EPSILON = 1.0E-7;
	private static final BlockShape SCAFFOLDING_BOTTOM = box(0.125);
	private static final BlockShape POWDER_SNOW_FALLING = box(0.9F);
	private static final BlockShape EMPTY = BlockShape.of(CollisionShape.EMPTY);

	private final @NotNull BlockReader reader;

	/**
	 * Creates the collisions of the world a player's client knows.
	 *
	 * @param reader the blocks the client knows
	 */
	public Collisions(@NotNull BlockReader reader) {
		this.reader = reader;
	}

	/**
	 * Collects the shapes of the blocks that overlap a box, in vanilla's order: east-west fastest, then up, then
	 * north-south, over the blocks around the box, leaving out the blocks of the outer layer unless their shape reaches
	 * beyond them.
	 *
	 * @param box the box
	 * @param context the moving entity
	 * @return the shapes at their blocks
	 */
	public @NotNull List<PlacedShape> blockCollisions(@NotNull Box box, @NotNull EntityContext context) {
		int[] min = {around(box.minX - EPSILON, -1), around(box.minY - EPSILON, -1), around(box.minZ - EPSILON, -1)};
		int[] max = {around(box.maxX + EPSILON, 1), around(box.maxY + EPSILON, 1), around(box.maxZ + EPSILON, 1)};
		List<PlacedShape> shapes = new ArrayList<>();
		for (int z = min[2]; z <= max[2]; z++)
			for (int y = min[1]; y <= max[1]; y++)
				for (int x = min[0]; x <= max[0]; x++) {
					int[] block = {x, y, z};
					int edges = edge(x, min[0], max[0]) + edge(y, min[1], max[1]) + edge(z, min[2], max[2]);
					PlacedShape placed = edges == 3 ? null : placed(block, edges, box, context);
					if (placed != null) shapes.add(placed);
				}

		return shapes;
	}

	private @Nullable PlacedShape placed(int @NotNull [] block, int edges, @NotNull Box box, @NotNull EntityContext who) {
		BlockState state = reader.state(block[0], block[1], block[2]);
		BlockShape shape = shape(state, block[1], who);
		if (shape.isEmpty()) return null;
		if (edges == 1 && !shape.isLarge()) return null;
		if (edges == 2 && state.block().kind() != BlockKind.MOVING_PISTON) return null;
		if (!shape.intersects(block, box)) return null;

		return new PlacedShape(shape, block);
	}

	/**
	 * The collision shape a block gives the entity: the data's shape, or for scaffolding and powder snow what their
	 * {@code getCollisionShape} gives an entity in its context. An entity wears no leather boots here, so it never walks
	 * on powder snow.
	 */
	private @NotNull BlockShape shape(@NotNull BlockState state, int blockY, @NotNull EntityContext context) {
		return switch (state.block().kind()) {
			case SCAFFOLDING -> scaffolding(state, blockY, context);
			case POWDER_SNOW -> context.fallDistance() > 2.5 ? POWDER_SNOW_FALLING : EMPTY;
			default -> reader.shape(state.collision());
		};
	}

	/**
	 * {@code ScaffoldingBlock.getCollisionShape}: the stable shape, which the data records, for an entity above the
	 * block that does not descend; the unstable bottom of a hanging bottom block for an entity above its bottom.
	 */
	private @NotNull BlockShape scaffolding(@NotNull BlockState state, int blockY, @NotNull EntityContext context) {
		if (context.isAbove(blockY, 1.0) && !context.descending()) return reader.shape(state.collision());

		boolean hanging = !"0".equals(state.property("distance").orElse("0"));
		boolean bottom = "true".equals(state.property("bottom").orElse("false"));
		return hanging && bottom && context.isAbove(blockY, 0.0) ? SCAFFOLDING_BOTTOM : EMPTY;
	}

	/**
	 * Tells whether no block overlaps a box ({@code noCollision}).
	 *
	 * @param box the box
	 * @param context the moving entity
	 * @return whether the box is free
	 */
	public boolean noCollision(@NotNull Box box, @NotNull EntityContext context) {
		return blockCollisions(box, context).isEmpty();
	}

	/**
	 * Moves a box against the blocks its movement passes ({@code collideBoundingBox}).
	 *
	 * @param movement the movement
	 * @param box the moving box
	 * @param context the moving entity
	 * @return the movement the blocks allow
	 */
	public @NotNull Vector collideBoundingBox(@NotNull Vector movement, @NotNull Box box, @NotNull EntityContext context) {
		return collideWithShapes(movement, box, blockCollisions(box.expandTowards(movement), context));
	}

	/**
	 * Moves a box against shapes one axis at a time, vertically first and then along the longer horizontal axis
	 * ({@code collideWithShapes}); a movement shorter than {@code 1.0E-7} before a shape is none ({@code Shapes.collide}).
	 *
	 * @param movement the movement
	 * @param box the moving box
	 * @param shapes the shapes in the way
	 * @return the movement the shapes allow
	 */
	public static @NotNull Vector collideWithShapes(
			@NotNull Vector movement,
			@NotNull Box box,
			@NotNull List<PlacedShape> shapes
	) {
		if (shapes.isEmpty()) return movement;

		Vector result = Vector.ZERO;
		Axis[] order = Math.abs(movement.x) < Math.abs(movement.z)
				? new Axis[] {Axis.Y, Axis.Z, Axis.X}
				: new Axis[] {Axis.Y, Axis.X, Axis.Z};
		for (Axis axis : order) {
			double distance = movement.get(axis);
			if (distance == 0.0) continue;

			result = result.with(axis, collide(axis, box.move(result), shapes, distance));
		}

		return result;
	}

	private static double collide(Axis axis, Box box, List<PlacedShape> shapes, double distance) {
		double allowed = distance;
		for (PlacedShape shape : shapes) {
			if (Math.abs(allowed) < EPSILON) return 0.0;

			allowed = shape.shape().collide(axis, shape.block(), box, allowed);
		}

		return allowed;
	}

	/**
	 * The heights a box at the bottom of {@code base} could step up to among shapes, ascending, as
	 * {@code collectCandidateStepUpHeights}: every coordinate of the shapes from the box's bottom up to the step height,
	 * except the height the collided movement already reached.
	 *
	 * @param base the box before stepping
	 * @param shapes the shapes around it
	 * @param stepHeight the entity's step height
	 * @param reached the height the collided movement reached
	 * @return the candidate heights
	 */
	public static float @NotNull [] candidateStepHeights(
			@NotNull Box base,
			@NotNull List<PlacedShape> shapes,
			float stepHeight,
			float reached
	) {
		TreeSet<Float> heights = new TreeSet<>();
		for (PlacedShape shape : shapes)
			for (double coordinate : shape.shape().coordinates(Axis.Y)) {
				float height = (float) (coordinate + shape.block()[1] - base.minY);
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
	 * @param box the box below the entity's feet
	 * @param position the entity's position
	 * @param context the moving entity
	 * @return the block's position, or {@code null} when no block is below
	 */
	public int @Nullable [] supportingBlock(@NotNull Box box, @NotNull Vector position, @NotNull EntityContext context) {
		int[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (PlacedShape shape : blockCollisions(box, context)) {
			int[] block = shape.block();
			double dx = block[0] + 0.5 - position.x;
			double dy = block[1] + 0.5 - position.y;
			double dz = block[2] + 0.5 - position.z;
			double distance = dx * dx + dy * dy + dz * dz;
			if (distance < bestDistance || distance == bestDistance && (best == null || compare(best, block) < 0)) {
				best = block;
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

	private static int around(double coordinate, int step) {
		return Coordinates.floor(coordinate) + step;
	}

	private static int edge(int value, int min, int max) {
		return value == min || value == max ? 1 : 0;
	}

	private static @NotNull BlockShape box(double height) {
		return BlockShape.of(new CollisionShape(List.of(new CollisionBox(0.0, 0.0, 0.0, 1.0, height, 1.0))));
	}
}
