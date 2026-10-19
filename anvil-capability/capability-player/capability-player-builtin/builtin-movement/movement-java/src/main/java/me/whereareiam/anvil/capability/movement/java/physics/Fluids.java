package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import me.whereareiam.anvil.protocol.java.data.model.CollisionBox;
import me.whereareiam.anvil.protocol.java.data.type.FluidType;
import org.jetbrains.annotations.NotNull;

/**
 * The fluids of the blocks the client knows, as vanilla's {@code FlowingFluid} describes them: how much of its block a
 * fluid fills, how high it reaches and which way it flows.
 */
final class Fluids {
	private static final int SOURCE = 8;
	/**
	 * North, east, south and west, in {@code Direction.Plane.HORIZONTAL}'s order, as x and z steps.
	 */
	private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	private final @NotNull BlockView view;

	Fluids(@NotNull BlockView view) {
		this.view = view;
	}

	@NotNull FluidType type(int x, int y, int z) {
		return view.state(x, y, z).getFluid();
	}

	/**
	 * The fluid's amount, from 1 to 8 for a source or a falling fluid, as {@code FluidState.getAmount}; 0 without fluid.
	 */
	int amount(@NotNull BlockState state) {
		if (state.getFluid() == FluidType.NONE) return 0;

		int level = level(state);
		if (level == 0 || level >= 8) return SOURCE;

		return 8 - level;
	}

	boolean falling(@NotNull BlockState state) {
		return state.getFluid() != FluidType.NONE && level(state) >= 8;
	}

	/**
	 * How much of its block the fluid itself fills ({@code getOwnHeight}).
	 */
	float ownHeight(@NotNull BlockState state) {
		return amount(state) / 9.0F;
	}

	/**
	 * How high the fluid of a block reaches within it ({@code FluidState.getHeight}): the whole block under the same
	 * fluid, else its own height.
	 */
	float height(int x, int y, int z) {
		BlockState state = view.state(x, y, z);
		if (state.getFluid() == FluidType.NONE) return 0.0F;
		if (view.state(x, y + 1, z).getFluid() == state.getFluid()) return 1.0F;

		return ownHeight(state);
	}

	/**
	 * The direction the fluid of a block flows, as {@code FlowingFluid.getFlow}: down its slope towards lower
	 * neighbours, and straight down beside a solid face while it falls.
	 */
	@NotNull Vector flow(int x, int y, int z) {
		BlockState state = view.state(x, y, z);
		FluidType type = state.getFluid();
		double flowX = 0.0;
		double flowZ = 0.0;
		for (int[] direction : HORIZONTAL) {
			int nx = x + direction[0];
			int nz = z + direction[1];
			BlockState neighbour = view.state(nx, y, nz);
			if (!affectsFlow(neighbour, type)) continue;

			float height = ownHeight(neighbour);
			float difference = 0.0F;
			if (height == 0.0F) {
				if (!blocksMotion(neighbour)) {
					BlockState below = view.state(nx, y - 1, nz);
					if (affectsFlow(below, type)) {
						height = ownHeight(below);
						if (height > 0.0F) difference = ownHeight(state) - (height - 0.8888889F);
					}
				}
			} else if (height > 0.0F) {
				difference = ownHeight(state) - height;
			}

			if (difference != 0.0F) {
				flowX += direction[0] * difference;
				flowZ += direction[1] * difference;
			}
		}

		Vector flow = new Vector(flowX, 0.0, flowZ);
		if (falling(state))
			for (int[] direction : HORIZONTAL) {
				int nx = x + direction[0];
				int nz = z + direction[1];
				if (solidFace(nx, y, nz, direction, type) || solidFace(nx, y + 1, nz, direction, type)) {
					flow = flow.normalize().add(0.0, -6.0, 0.0);
					break;
				}
			}

		return flow.normalize();
	}

	/**
	 * Tells whether a box holds any fluid ({@code containsAnyLiquid}).
	 */
	boolean containsAny(@NotNull Box box) {
		int minX = floor(box.minX);
		int maxX = ceil(box.maxX);
		int minY = floor(box.minY);
		int maxY = ceil(box.maxY);
		int minZ = floor(box.minZ);
		int maxZ = ceil(box.maxZ);
		for (int x = minX; x < maxX; x++)
			for (int y = minY; y < maxY; y++)
				for (int z = minZ; z < maxZ; z++)
					if (type(x, y, z) != FluidType.NONE) return true;

		return false;
	}

	private static boolean affectsFlow(@NotNull BlockState state, @NotNull FluidType type) {
		return state.getFluid() == FluidType.NONE || state.getFluid() == type;
	}

	/**
	 * Whether a block stops movement ({@code BlockState.blocksMotion}), which vanilla derives from its collision
	 * shape: a box at least about three quarters of a block on average, or a full block high.
	 */
	private static boolean blocksMotion(@NotNull BlockState state) {
		if (state.getCollision().isEmpty()) return false;

		double minX = 1.0;
		double minY = 1.0;
		double minZ = 1.0;
		double maxX = 0.0;
		double maxY = 0.0;
		double maxZ = 0.0;
		for (CollisionBox box : state.getCollision().getBoxes()) {
			minX = Math.min(minX, box.getMinX());
			minY = Math.min(minY, box.getMinY());
			minZ = Math.min(minZ, box.getMinZ());
			maxX = Math.max(maxX, box.getMaxX());
			maxY = Math.max(maxY, box.getMaxY());
			maxZ = Math.max(maxZ, box.getMaxZ());
		}
		Box bounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);

		return bounds.size() >= 0.7291666666666666 || maxY - minY >= 1.0;
	}

	/**
	 * Whether a block shows a solid face in a horizontal direction ({@code FlowingFluid.isSolidFace}): not the same
	 * fluid, not ice, and its collision covering that whole face.
	 */
	private boolean solidFace(int x, int y, int z, int @NotNull [] direction, @NotNull FluidType type) {
		BlockState state = view.state(x, y, z);
		if (state.getFluid() == type) return false;
		if (state.getBlock().getName().equals("minecraft:ice") || state.getBlock().getName().equals("minecraft:frosted_ice"))
			return false;

		for (CollisionBox box : state.getCollision().getBoxes()) {
			boolean coversY = box.getMinY() <= 0.0 && box.getMaxY() >= 1.0;
			if (direction[0] != 0 && coversY && box.getMinZ() <= 0.0 && box.getMaxZ() >= 1.0
					&& (direction[0] < 0 ? box.getMinX() <= 0.0 : box.getMaxX() >= 1.0)) return true;
			if (direction[1] != 0 && coversY && box.getMinX() <= 0.0 && box.getMaxX() >= 1.0
					&& (direction[1] < 0 ? box.getMinZ() <= 0.0 : box.getMaxZ() >= 1.0)) return true;
		}

		return false;
	}

	private static int level(@NotNull BlockState state) {
		return state.property("level").map(Integer::parseInt).orElse(0);
	}

	static int floor(double value) {
		int truncated = (int) value;
		return value < truncated ? truncated - 1 : truncated;
	}

	static int ceil(double value) {
		int truncated = (int) value;
		return value > truncated ? truncated + 1 : truncated;
	}
}
