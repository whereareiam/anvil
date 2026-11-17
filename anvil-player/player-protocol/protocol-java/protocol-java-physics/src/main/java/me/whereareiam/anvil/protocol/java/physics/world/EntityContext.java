package me.whereareiam.anvil.protocol.java.physics.world;

import lombok.Value;

/**
 * What a block's collision shape may depend on of the moving entity, as vanilla's {@code EntityCollisionContext} hands
 * it to {@code getCollisionShape}: the height of the entity's feet, whether it descends, which it does while its
 * shift key is down ({@code Entity.isDescending}), and how far it has fallen.
 */
@Value
public class EntityContext {
	/**
	 * The height of the entity's feet ({@code entityBottom}).
	 */
	double bottom;
	/**
	 * Whether the entity descends.
	 */
	boolean descending;
	/**
	 * How far the entity has fallen.
	 */
	double fallDistance;

	/**
	 * Tells whether the entity stands above a shape's top at a block, as {@code EntityCollisionContext.isAbove} does.
	 *
	 * @param blockY the block's height
	 * @param shapeTop the top of the shape, in block-local coordinates
	 * @return whether the entity's feet are above it
	 */
	public boolean isAbove(int blockY, double shapeTop) {
		return bottom > blockY + shapeTop - 1.0E-5F;
	}
}
