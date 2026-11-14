package me.whereareiam.anvil.capability.worlds.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A place in one of a server's worlds, with the direction a player there looks in.
 */
@Value
@Builder(toBuilder = true)
public class WorldLocation {
	/**
	 * The key of the world's level, with its namespace, such as {@code minecraft:the_nether}.
	 */
	@NotNull String world;
	/**
	 * The position along the east-west axis.
	 */
	double x;
	/**
	 * The height.
	 */
	double y;
	/**
	 * The position along the north-south axis.
	 */
	double z;
	/**
	 * The horizontal view angle in degrees, where {@code 0} faces south.
	 */
	float yaw;
	/**
	 * The vertical view angle in degrees, where {@code 0} is level and {@code 90} faces down.
	 */
	float pitch;
}
