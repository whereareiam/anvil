package me.whereareiam.anvil.edition.java.data.block.type;

/**
 * How vanilla decides a block's collision shape, and so whether the collision shape of each of its states holds for
 * every entity and position.
 *
 * <p>Anvil maintains these by hand from vanilla's code, because minecraft-data does not carry them; each constant
 * names the vanilla code it stands for.</p>
 */
public enum CollisionKind {
	/**
	 * The state's collision shape is the shape every entity collides with: most blocks.
	 */
	FIXED,
	/**
	 * Vanilla's collision shape depends on the entity, not only on the state, so the data's shape is one of several:
	 * scaffolding (solid only from above), powder snow (solid only on leather boots or while falling), shulker boxes
	 * (their lid) and moving pistons.
	 */
	DYNAMIC,
	/**
	 * Vanilla moves the collision shape horizontally by a hash of the block's position
	 * ({@code BlockBehaviour.OffsetType.XZ}): bamboo and pointed dripstone. The data's shape is the unmoved one.
	 */
	OFFSET
}
