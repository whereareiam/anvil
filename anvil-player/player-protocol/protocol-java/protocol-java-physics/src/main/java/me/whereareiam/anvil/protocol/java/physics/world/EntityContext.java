package me.whereareiam.anvil.protocol.java.physics.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.block.model.collision.CollisionCase;
import me.whereareiam.anvil.protocol.java.block.type.EntityBottom;
import org.jetbrains.annotations.NotNull;

/**
 * What a block's collision shape may depend on of the moving entity, as vanilla's {@code EntityCollisionContext} hands
 * it to {@code getCollisionShape}: the height of the entity's feet, whether it descends, which it does while its
 * shift key is down ({@code Entity.isDescending}), how far it has fallen, and whether it walks on powder snow.
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
	 * Whether the entity walks on powder snow ({@code PowderSnowBlock.canEntityWalkOnPowderSnow}), as leather boots
	 * let a living entity.
	 */
	boolean walksOnPowderSnow;

	/**
	 * Returns the player case the block data records the shape of a block for this entity in: where the entity's feet
	 * are relative to the block, as the context-dependent blocks ask {@code CollisionContext.isAbove} with the full
	 * block and the block below it, whether it descends, whether it has fallen more than {@code 2.5} blocks, as powder
	 * snow asks, and whether it walks on powder snow.
	 *
	 * @param blockY the block's height
	 * @return the entity's case at that block
	 */
	public @NotNull CollisionCase collisionCase(int blockY) {
		return new CollisionCase(bottom(blockY), descending, fallDistance > 2.5, walksOnPowderSnow);
	}

	private @NotNull EntityBottom bottom(int blockY) {
		if (isAbove(blockY, 1.0)) return EntityBottom.ABOVE;
		if (isAbove(blockY, 0.0)) return EntityBottom.INSIDE;

		return EntityBottom.BELOW;
	}

	/**
	 * Tells whether the entity stands above a shape's top at a block, as {@code EntityCollisionContext.isAbove} does.
	 */
	private boolean isAbove(int blockY, double shapeTop) {
		return bottom > blockY + shapeTop - 1.0E-5F;
	}
}
