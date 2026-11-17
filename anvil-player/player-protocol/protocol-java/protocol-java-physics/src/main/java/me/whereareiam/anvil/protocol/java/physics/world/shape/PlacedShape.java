package me.whereareiam.anvil.protocol.java.physics.world.shape;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A block's collision shape at the block's position, as {@code VoxelShape.move} places it for a collision.
 */
@Value
public class PlacedShape {
	/**
	 * The shape in block-local coordinates.
	 */
	@NotNull BlockShape shape;
	/**
	 * The block's position as east-west, height and north-south coordinates.
	 */
	int @NotNull [] block;
}
