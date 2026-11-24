package me.whereareiam.anvil.testkit.physics.harness.block;

import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One block state as the client states it.
 */
@Value
public class VanillaState {
	/**
	 * The global state ID.
	 */
	int id;

	/**
	 * The collision shape for a player standing on the block ({@link CollisionCase#STANDING}), unmoved for an offset
	 * block.
	 */
	List<ShapeBox> collision;

	/**
	 * The collision shape of each other case whose shape differs from {@link #collision}.
	 */
	Map<CollisionCase, List<ShapeBox>> cases;

	/**
	 * The state's fluid.
	 */
	VanillaFluid fluid;

	/**
	 * Whether the state stops a fluid beside it from flowing, {@link BlockProbe#blocksFluidFlow}.
	 */
	boolean blocksFluidFlow;

	/**
	 * The state's sturdy faces, {@link BlockProbe#sturdyFaces}.
	 */
	Set<BlockFace> sturdyFaces;
}
