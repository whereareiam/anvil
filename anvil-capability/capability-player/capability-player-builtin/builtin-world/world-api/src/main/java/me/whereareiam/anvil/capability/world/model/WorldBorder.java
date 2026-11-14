package me.whereareiam.anvil.capability.world.model;

import lombok.Builder;
import lombok.Value;

/**
 * The world border of the player's level as the client was told it last: a square around a center, which the server
 * may set to a size at once or move from one size to another over time.
 */
@Value
@Builder(toBuilder = true)
public class WorldBorder {
	/**
	 * The east-west coordinate of the border's center.
	 */
	double centerX;
	/**
	 * The north-south coordinate of the border's center.
	 */
	double centerZ;
	/**
	 * The side length in blocks the border has, or moves to while it moves.
	 */
	double size;
	/**
	 * The side length in blocks the border started moving from, or {@link #getSize()} when the server set the size at
	 * once.
	 */
	double lerpFrom;
	/**
	 * How long the border takes to move from {@link #getLerpFrom()} to {@link #getSize()}, in ticks, counted from when
	 * the client was told; {@code 0} when the server set the size at once. Releases before Minecraft 1.21.9 send
	 * milliseconds, which the client reports as ticks of 50 milliseconds.
	 */
	long lerpTicks;
	/**
	 * The largest side length the border may grow to.
	 */
	int absoluteMaxSize;
	/**
	 * How many blocks from the border the client starts tinting the screen.
	 */
	int warningBlocks;
	/**
	 * How many seconds before a moving border reaches the player the client starts tinting the screen.
	 */
	int warningTime;
}
