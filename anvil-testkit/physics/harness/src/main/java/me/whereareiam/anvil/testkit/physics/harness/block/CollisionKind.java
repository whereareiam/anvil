package me.whereareiam.anvil.testkit.physics.harness.block;

/**
 * How vanilla decides a block's collision shapes, as {@link BlockTableReader} finds it: the names of
 * {@code data-block}'s {@code CollisionKind}.
 */
public enum CollisionKind {
	/**
	 * Every state's shape is the same for every player and position.
	 */
	FIXED,
	/**
	 * A state's shape depends on the player ({@link VanillaState#getCases()}), or on the block entity
	 * ({@code hasDynamicShape} without an offset), which the data reads as absent.
	 */
	DYNAMIC,
	/**
	 * The shapes move by a hash of their position ({@link BlockOffset}); the data's shapes are the unmoved ones.
	 */
	OFFSET
}
