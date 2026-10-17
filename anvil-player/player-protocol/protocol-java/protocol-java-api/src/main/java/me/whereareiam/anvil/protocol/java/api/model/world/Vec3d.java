package me.whereareiam.anvil.protocol.java.api.model.world;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Three coordinates in blocks, as the protocol carries the position of the player, of an entity or of a sound, or a
 * point relative to an entity.
 */
@Value
public class Vec3d {
	/**
	 * The vector whose coordinates are all zero, such as an entity's own position relative to itself.
	 */
	public static final @NotNull Vec3d ZERO = new Vec3d(0, 0, 0);

	/**
	 * The east-west coordinate.
	 */
	double x;
	/**
	 * The vertical coordinate; of a player or an entity, the height of its feet.
	 */
	double y;
	/**
	 * The north-south coordinate.
	 */
	double z;
}
