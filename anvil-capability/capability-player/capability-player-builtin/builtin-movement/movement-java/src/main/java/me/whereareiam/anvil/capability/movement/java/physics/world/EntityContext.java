package me.whereareiam.anvil.capability.movement.java.physics.world;

import lombok.Value;

/**
 * What a block's collision shape may depend on of the moving player, as vanilla's {@code EntityCollisionContext} hands
 * it to {@code getCollisionShape}: the height of the player's feet, whether it descends, which it does while it holds
 * sneak ({@code Entity.isDescending}), and how far it has fallen.
 */
@Value
public class EntityContext {
	/**
	 * The height of the player's feet ({@code entityBottom}).
	 */
	double bottom;
	/**
	 * Whether the player descends.
	 */
	boolean descending;
	/**
	 * How far the player has fallen.
	 */
	double fallDistance;

	/**
	 * Tells whether the player stands above a shape's top at a block, as {@code EntityCollisionContext.isAbove} does.
	 *
	 * @param blockY the block's height
	 * @param shapeTop the top of the shape, in block-local coordinates
	 * @return whether the player's feet are above it
	 */
	public boolean isAbove(int blockY, double shapeTop) {
		return bottom > blockY + shapeTop - 1.0E-5F;
	}
}
