package me.whereareiam.anvil.protocol.java.block.type;

/**
 * How vanilla decides a block's collision shape, and so whether the collision shape of each of its states holds for
 * every entity and position.
 *
 * <p>Each block's kind is read from its release's vanilla client: whether its shapes differ between player cases,
 * whether it has an offset, and whether vanilla caches its shapes ({@code hasDynamicShape}).</p>
 */
public enum CollisionKind {
	/**
	 * The state's collision shape is the shape every entity collides with: most blocks.
	 */
	FIXED,
	/**
	 * Vanilla's collision shape depends on more than the state: on the player, for which the state records the shape of
	 * each case that differs ({@code BlockState.getCollisionCases}), as for scaffolding and powder snow; or on the
	 * block entity ({@code hasDynamicShape}), whose shape the data records without one, as for shulker boxes (closed)
	 * and moving pistons (empty).
	 */
	DYNAMIC,
	/**
	 * Vanilla moves the block's shapes by a hash of the block's position ({@code BlockState.getOffset}), as the block's
	 * {@code BlockType.getOffset} describes: bamboo and pointed dripstone among the blocks with collision, flowers and
	 * grass among those without. The data's shape is the unmoved one.
	 */
	OFFSET
}
