package me.whereareiam.anvil.edition.java.data.block.type;

/**
 * A vanilla movement behaviour of a block that its movement numbers alone do not describe.
 *
 * <p>Each release's behaviours are read from its vanilla client, by the tag or class its movement code asks; each
 * constant names that code.</p>
 */
public enum MovementBehaviour {
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
