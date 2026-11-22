package me.whereareiam.anvil.testkit.physics.harness.block;

import java.util.List;
import java.util.Set;

/**
 * Reads the collision shapes, offsets and sturdy faces of a bootstrapped vanilla client's blocks, for
 * {@link BlockTableReader}, as part of a {@link BlockProbe}. Every answer is the client's own.
 *
 * @param <B> the client's block type
 * @param <S> the client's block state type
 */
public interface ShapeProbe<B, S> {
	/**
	 * The state's collision shape at a block position for a player in {@code context}, through
	 * {@code BlockState.getCollisionShape(getter, position, CollisionContext.of(player))} with a getter holding no
	 * block entity and no neighbour, as {@code toAabbs} gives it.
	 */
	List<ShapeBox> collision(S state, int x, int y, int z, CollisionCase context);

	/**
	 * Whether the block's shapes move by a hash of their position ({@code getOffsetType} until 1.19.3,
	 * {@code hasOffsetFunction} from 1.19.4).
	 */
	boolean offset(B block);

	/**
	 * The offset of the state's shapes at a block position, {@code BlockState.getOffset}, as {@code x, y, z}.
	 */
	double[] offset(S state, int x, int y, int z);

	/**
	 * The seed vanilla's offset hashes from a block column, {@code Mth.getSeed(x, 0, z)}.
	 */
	long offsetSeed(int x, int z);

	/**
	 * Whether the block's shapes can depend on more than the state, {@code Block.hasDynamicShape}, so vanilla caches
	 * none of them.
	 */
	boolean dynamicShape(B block);

	/**
	 * The faces of the state that are sturdy, {@code BlockState.isFaceSturdy} with a getter holding no block entity
	 * and no neighbour: a falling fluid beside such a face flows down.
	 */
	Set<BlockFace> sturdyFaces(S state);
}
