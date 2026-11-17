package me.whereareiam.anvil.protocol.java.block.type;

/**
 * What a block is, where vanilla's movement code asks by the block's class or tag rather than by its numbers.
 *
 * <p>Each release's kinds are read from its vanilla client, by the class, identity or tag its movement code asks;
 * each constant names that code.</p>
 */
public enum BlockKind {
	/**
	 * None of the kinds below: most blocks.
	 */
	OTHER,
	/**
	 * The block's states are air ({@code BlockState.isAir}): {@code air}, {@code cave_air} and {@code void_air}.
	 */
	AIR,
	/**
	 * The block is a fence gate ({@code FenceGateBlock}), whose closed collision reaches above the block:
	 * {@code Entity.getOnPos} keeps it as the block an entity on top of it stands on, as for the
	 * {@code minecraft:fences} and {@code minecraft:walls} tags.
	 */
	FENCE_GATE,
	/**
	 * The block is a trapdoor ({@code TrapDoorBlock}): {@code LivingEntity.onClimbable} lets an entity in it climb
	 * while its {@code open} property is {@code true} and a {@link #LADDER} whose {@code facing} is the trapdoor's is
	 * below it.
	 */
	TRAPDOOR,
	/**
	 * The block is the ladder ({@code Blocks.LADDER}), which {@code LivingEntity.trapdoorUsableAsLadder} looks for below
	 * an open trapdoor.
	 */
	LADDER,
	/**
	 * The block is scaffolding ({@code ScaffoldingBlock}): {@code LivingEntity.handleOnClimbable} lets an entity in it
	 * sink while it holds sneak, and its collision shape depends on whether the entity stands above it and descends.
	 */
	SCAFFOLDING,
	/**
	 * The block is powder snow ({@code PowderSnowBlock}): its {@code entityInside} slows a living entity only while the
	 * block the entity's feet are in is powder snow too, and its collision shape depends on the entity's fall distance.
	 */
	POWDER_SNOW,
	/**
	 * The block is water itself ({@code Blocks.WATER}), not a waterlogged block: {@code Entity.getBlockSpeedFactor}
	 * takes the factor of the block an entity is in without looking below it when that block is water.
	 */
	WATER,
	/**
	 * The block is an {@code IceBlock}, ice or frosted ice: {@code FlowingFluid.isSolidFace} does not count its faces,
	 * so falling fluid beside it does not flow straight down.
	 */
	ICE,
	/**
	 * The block is a moving piston ({@code Blocks.MOVING_PISTON}): {@code BlockCollisions} keeps its shape even on the
	 * edges of the area around a box, where it skips every other block.
	 */
	MOVING_PISTON
}
