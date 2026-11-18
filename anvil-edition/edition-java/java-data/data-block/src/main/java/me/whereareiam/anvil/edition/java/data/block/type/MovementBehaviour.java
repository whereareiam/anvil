package me.whereareiam.anvil.edition.java.data.block.type;

/**
 * A vanilla movement behaviour of a block that its movement numbers alone do not describe.
 *
 * <p>Anvil maintains these by hand from vanilla's code, because minecraft-data does not carry them; each constant
 * names the vanilla code it stands for.</p>
 */
public enum MovementBehaviour {
	/**
	 * The block is in the {@code minecraft:climbable} tag, so an entity in it climbs: ladders, vines and scaffolding.
	 * Vanilla also lets an entity climb an open trapdoor above a ladder that faces the same way, which
	 * {@link BlockKind#TRAPDOOR} marks, and climb powder snow on leather boots, which depends on more than the block.
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
	BUBBLE_COLUMN
}
