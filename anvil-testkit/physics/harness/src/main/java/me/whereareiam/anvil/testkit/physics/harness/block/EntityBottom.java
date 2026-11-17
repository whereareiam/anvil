package me.whereareiam.anvil.testkit.physics.harness.block;

/**
 * Where the bottom of a player's box is relative to a block, the input of {@code CollisionContext.isAbove}, which
 * vanilla's context-dependent blocks ask with the full block ({@code Shapes.block()}) or the block below it: one value
 * on each side of each of those two thresholds, {@code 1.0E-5F} below the block's top and its bottom.
 */
public enum EntityBottom {
	/**
	 * On the block's top, as a player standing on it: above the block and above the block below it.
	 */
	ABOVE(1.0),
	/**
	 * Halfway up the block: inside it, above the block below it.
	 */
	INSIDE(0.5),
	/**
	 * A block below the block's bottom: above neither.
	 */
	BELOW(-1.0);

	private final double height;

	EntityBottom(double height) {
		this.height = height;
	}

	/**
	 * The height of the player's feet above the block's bottom.
	 */
	public double height() {
		return height;
	}
}
