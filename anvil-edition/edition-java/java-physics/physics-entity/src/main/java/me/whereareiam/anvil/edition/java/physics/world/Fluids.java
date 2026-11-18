package me.whereareiam.anvil.edition.java.physics.world;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Coordinates;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * The fluids of the blocks the client knows, as vanilla's {@code FlowingFluid} describes them: how much of its block a
 * fluid fills, how high it reaches and which way it flows. A block that stops movement ({@code blocksMotion}) and a
 * face that stops a falling fluid ({@code isSolidFace}) are read from the block's collision shape, which is what
 * vanilla derives them from for the blocks that do not force them.
 */
public final class Fluids {
	private static final int SOURCE = 8;
	/**
	 * North, east, south and west, in {@code Direction.Plane.HORIZONTAL}'s order, as x and z steps.
	 */
	private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	private final @NotNull BlockReader reader;

	/**
	 * Creates the fluids of the world a player's client knows.
	 *
	 * @param reader the blocks the client knows
	 */
	public Fluids(@NotNull BlockReader reader) {
		this.reader = reader;
	}

	/**
	 * Returns the fluid of a block.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @return the fluid, {@link FluidType#NONE} without one
	 */
	public @NotNull FluidType type(int x, int y, int z) {
		return reader.state(x, y, z).getFluid();
	}

	/**
	 * How high the fluid of a block reaches within it ({@code FluidState.getHeight}): the whole block under the same
	 * fluid, else its own height.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @return the height, {@code 0} without fluid
	 */
	public float height(int x, int y, int z) {
		BlockState state = reader.state(x, y, z);
		if (state.getFluid() == FluidType.NONE) return 0.0F;
		if (reader.state(x, y + 1, z).getFluid() == state.getFluid()) return 1.0F;

		return ownHeight(state);
	}

	/**
	 * The direction the fluid of a block flows, as {@code FlowingFluid.getFlow}: down its slope towards lower
	 * neighbours, and straight down beside a solid face while it falls.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @return the flow's direction, of length one or zero
	 */
	public @NotNull Vector flow(int x, int y, int z) {
		BlockState state = reader.state(x, y, z);
		double flowX = 0.0;
		double flowZ = 0.0;
		for (int[] direction : HORIZONTAL) {
			float difference = difference(state, x + direction[0], y, z + direction[1]);
			if (difference == 0.0F) continue;

			flowX += direction[0] * difference;
			flowZ += direction[1] * difference;
		}

		Vector flow = new Vector(flowX, 0.0, flowZ);
		if (falling(state) && besideSolidFace(state.getFluid(), x, y, z)) flow = flow.normalize().add(0.0, -6.0, 0.0);

		return flow.normalize();
	}

	private float difference(@NotNull BlockState state, int x, int y, int z) {
		BlockState neighbour = reader.state(x, y, z);
		if (!affectsFlow(neighbour, state.getFluid())) return 0.0F;

		float height = ownHeight(neighbour);
		if (height != 0.0F) return ownHeight(state) - height;
		if (blocksMotion(neighbour)) return 0.0F;

		BlockState below = reader.state(x, y - 1, z);
		if (!affectsFlow(below, state.getFluid())) return 0.0F;

		float belowHeight = ownHeight(below);
		return belowHeight > 0.0F ? ownHeight(state) - (belowHeight - 0.8888889F) : 0.0F;
	}

	private boolean besideSolidFace(@NotNull FluidType type, int x, int y, int z) {
		for (int[] direction : HORIZONTAL)
			if (solidFace(x + direction[0], y, z + direction[1], direction, type)
					|| solidFace(x + direction[0], y + 1, z + direction[1], direction, type)) return true;

		return false;
	}

	/**
	 * Tells whether a box holds any fluid ({@code containsAnyLiquid}).
	 *
	 * @param box the box
	 * @return whether a block it touches holds fluid
	 */
	public boolean containsAny(@NotNull Box box) {
		for (int x = Coordinates.floor(box.minX); x < Coordinates.ceil(box.maxX); x++)
			for (int y = Coordinates.floor(box.minY); y < Coordinates.ceil(box.maxY); y++)
				for (int z = Coordinates.floor(box.minZ); z < Coordinates.ceil(box.maxZ); z++)
					if (type(x, y, z) != FluidType.NONE) return true;

		return false;
	}

	/**
	 * The fluid's amount, from 1 to 8 for a source or a falling fluid, as {@code FluidState.getAmount}; 0 without fluid.
	 */
	private static int amount(@NotNull BlockState state) {
		if (state.getFluid() == FluidType.NONE) return 0;

		int level = level(state);
		return level == 0 || level >= SOURCE ? SOURCE : SOURCE - level;
	}

	private static boolean falling(@NotNull BlockState state) {
		return state.getFluid() != FluidType.NONE && level(state) >= SOURCE;
	}

	/**
	 * How much of its block the fluid itself fills ({@code getOwnHeight}).
	 */
	private static float ownHeight(@NotNull BlockState state) {
		return amount(state) / 9.0F;
	}

	private static boolean affectsFlow(@NotNull BlockState state, @NotNull FluidType type) {
		return state.getFluid() == FluidType.NONE || state.getFluid() == type;
	}

	/**
	 * Whether a block stops movement ({@code BlockState.blocksMotion}), which vanilla derives from its collision shape
	 * ({@code calculateSolid}): bounds of at least about three quarters of a block on average, or a full block high.
	 */
	private static boolean blocksMotion(@NotNull BlockState state) {
		if (state.getCollision().isEmpty()) return false;

		Box bounds = null;
		for (CollisionBox box : state.getCollision().getBoxes()) {
			Box part = new Box(new Vector(box.getMinX(), box.getMinY(), box.getMinZ()),
					new Vector(box.getMaxX(), box.getMaxY(), box.getMaxZ()));
			bounds = bounds == null ? part : bounds.union(part);
		}

		return bounds.size() >= 0.7291666666666666 || bounds.maxY - bounds.minY >= 1.0;
	}

	/**
	 * Whether a block shows a solid face in a horizontal direction ({@code FlowingFluid.isSolidFace}): not the same
	 * fluid, not ice ({@code IceBlock}), and one box of its collision covering that whole face.
	 */
	private boolean solidFace(int x, int y, int z, int @NotNull [] direction, @NotNull FluidType type) {
		BlockState state = reader.state(x, y, z);
		if (state.getFluid() == type || state.getBlock().getKind() == BlockKind.ICE) return false;

		for (CollisionBox box : state.getCollision().getBoxes())
			if (coversFace(box, direction)) return true;

		return false;
	}

	private static boolean coversFace(@NotNull CollisionBox box, int @NotNull [] direction) {
		if (box.getMinY() > 0.0 || box.getMaxY() < 1.0) return false;
		if (direction[0] != 0)
			return box.getMinZ() <= 0.0 && box.getMaxZ() >= 1.0
					&& (direction[0] < 0 ? box.getMinX() <= 0.0 : box.getMaxX() >= 1.0);

		return box.getMinX() <= 0.0 && box.getMaxX() >= 1.0
				&& (direction[1] < 0 ? box.getMinZ() <= 0.0 : box.getMaxZ() >= 1.0);
	}

	private static int level(@NotNull BlockState state) {
		return state.property("level").map(Integer::parseInt).orElse(0);
	}
}
