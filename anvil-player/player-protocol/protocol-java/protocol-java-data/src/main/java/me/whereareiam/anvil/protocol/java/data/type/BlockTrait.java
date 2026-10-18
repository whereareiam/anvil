package me.whereareiam.anvil.protocol.java.data.type;

/**
 * A vanilla movement behaviour of a block that its numbers alone do not describe.
 *
 * <p>Anvil maintains these by hand from vanilla's code, because minecraft-data does not carry them; each constant
 * names the vanilla code it stands for.</p>
 */
public enum BlockTrait {
	/**
	 * The block's states are air ({@code BlockState.isAir}): {@code air}, {@code cave_air} and {@code void_air}.
	 */
	AIR,
	/**
	 * The block is in the {@code minecraft:climbable} tag, so an entity in it climbs: ladders, vines and scaffolding.
	 * Vanilla also lets an entity climb an open trapdoor above a ladder that faces the same way, which
	 * {@link #TRAPDOOR} marks, and climb powder snow on leather boots, which depends on more than the block.
	 */
	CLIMBABLE,
	/**
	 * A slime block's {@code stepOn}: an entity walking on it without stepping carefully is slowed by
	 * {@code 0.4 + |motionY| * 0.2} horizontally while its vertical speed is below {@code 0.1}.
	 */
	SLIME_STEP,
	/**
	 * A honey block's {@code entityInside}: an entity against its side slides down slowly instead of falling.
	 */
	HONEY_SLIDE,
	/**
	 * A bubble column pushes an entity up, or drags it down where its {@code drag} property is {@code true}.
	 */
	BUBBLE_COLUMN,
	/**
	 * Vanilla's collision shape depends on the entity, not only on the state, so the data's shape is one of several:
	 * scaffolding (solid only from above), powder snow (solid only on leather boots or while falling), shulker boxes
	 * (their lid) and moving pistons.
	 */
	DYNAMIC_COLLISION,
	/**
	 * Vanilla moves the collision shape horizontally by a hash of the block's position
	 * ({@code BlockBehaviour.OffsetType.XZ}): bamboo and pointed dripstone. The data's shape is the unmoved one.
	 */
	OFFSET_COLLISION,
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
