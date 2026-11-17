package me.whereareiam.anvil.protocol.java.physics.world;

import me.whereareiam.anvil.protocol.java.block.model.BlockState;
import me.whereareiam.anvil.protocol.java.block.type.BlockFace;
import me.whereareiam.anvil.protocol.java.block.type.BlockKind;
import me.whereareiam.anvil.protocol.java.block.type.FluidType;
import me.whereareiam.anvil.protocol.java.geometry.Box;
import me.whereareiam.anvil.protocol.java.geometry.Coordinates;
import me.whereareiam.anvil.protocol.java.geometry.Vector;
import me.whereareiam.anvil.protocol.java.geometry.VectorLength;
import org.jetbrains.annotations.NotNull;

/**
 * The fluids of the blocks the client knows, as vanilla's {@code FlowingFluid} describes them: how much of its block a
 * fluid fills, how high it reaches and which way it flows. Each block state's fluid amount, whether it falls, whether
 * it stops fluid flowing beside it and its sturdy faces are the block data's, as the release's vanilla client states
 * them.
 */
public final class Fluids {
	/**
	 * North, east, south and west, in {@code Direction.Plane.HORIZONTAL}'s order, as x and z steps.
	 */
	private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

	private final @NotNull BlockReader reader;
	private final @NotNull VectorLength vectors;

	/**
	 * Creates the fluids of the world a player's client knows.
	 *
	 * @param reader the blocks the client knows
	 * @param vectors the length and direction of vectors the client computes with
	 */
	public Fluids(@NotNull BlockReader reader, @NotNull VectorLength vectors) {
		this.reader = reader;
		this.vectors = vectors;
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
		if (state.isFluidFalling() && besideSolidFace(state.getFluid(), x, y, z))
			flow = vectors.normalize(flow).add(0.0, -6.0, 0.0);

		return vectors.normalize(flow);
	}

	private float difference(@NotNull BlockState state, int x, int y, int z) {
		BlockState neighbour = reader.state(x, y, z);
		if (!affectsFlow(neighbour, state.getFluid())) return 0.0F;

		float height = ownHeight(neighbour);
		if (height != 0.0F) return ownHeight(state) - height;
		if (neighbour.isBlocksFluidFlow()) return 0.0F;

		BlockState below = reader.state(x, y - 1, z);
		if (!affectsFlow(below, state.getFluid())) return 0.0F;

		float belowHeight = ownHeight(below);
		return belowHeight > 0.0F ? ownHeight(state) - (belowHeight - 0.8888889F) : 0.0F;
	}

	private boolean besideSolidFace(@NotNull FluidType type, int x, int y, int z) {
		for (int side = 0; side < HORIZONTAL.length; side++) {
			int[] direction = HORIZONTAL[side];
			if (solidFace(x + direction[0], y, z + direction[1], FACES[side], type)
					|| solidFace(x + direction[0], y + 1, z + direction[1], FACES[side], type)) return true;
		}

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
	 * How much of its block the fluid itself fills ({@code getOwnHeight}): its amount in ninths.
	 */
	private static float ownHeight(@NotNull BlockState state) {
		return state.getFluidAmount() / 9.0F;
	}

	private static boolean affectsFlow(@NotNull BlockState state, @NotNull FluidType type) {
		return state.getFluid() == FluidType.NONE || state.getFluid() == type;
	}

	/**
	 * Whether a block shows a solid face toward a direction ({@code FlowingFluid.isSolidFace}): not the same fluid, not
	 * an {@code IceBlock}, and sturdy on the face toward that direction, which vanilla asks of the neighbour's face on
	 * the far side from the fluid.
	 */
	private boolean solidFace(int x, int y, int z, @NotNull BlockFace face, @NotNull FluidType type) {
		BlockState state = reader.state(x, y, z);
		if (state.getFluid() == type || state.getBlock().getKind() == BlockKind.ICE) return false;

		return state.getSturdyFaces().contains(face);
	}
}
