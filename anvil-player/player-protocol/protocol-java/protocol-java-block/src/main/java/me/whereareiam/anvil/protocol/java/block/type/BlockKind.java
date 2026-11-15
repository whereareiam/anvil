package me.whereareiam.anvil.protocol.java.block.type;

/**
 * What a block is, where vanilla's movement code asks by the block's class or tag rather than by its numbers.
 *
 * <p>Anvil maintains these by hand from vanilla's code, because minecraft-data does not carry them; each constant
 * names the vanilla code it stands for.</p>
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
	 * The block is a fence (the {@code minecraft:fences} tag), whose collision reaches above the block:
	 * {@code Entity.getOnPos} keeps it as the block an entity on top of it stands on for what lies just below the
	 * entity's feet, such as the block it lands on.
	 */
	FENCE,
	/**
	 * The block is a wall (the {@code minecraft:walls} tag), whose collision reaches above the block:
	 * {@code Entity.getOnPos} keeps it as the block an entity on top of it stands on, so its friction and its speed
	 * and jump factors apply.
	 */
	WALL,
	/**
	 * The block is a fence gate ({@code FenceGateBlock}), whose closed collision reaches above the block:
	 * {@code Entity.getOnPos} keeps it as the block an entity on top of it stands on, as for a wall.
	 */
	FENCE_GATE,
	/**
	 * The block is a trapdoor ({@code TrapDoorBlock}): {@code LivingEntity.onClimbable} lets an entity in it climb
	 * while its {@code open} property is {@code true} and a ladder whose {@code facing} is the trapdoor's is below it.
	 */
	TRAPDOOR
}
