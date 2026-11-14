package me.whereareiam.anvil.capability.world.model;

import lombok.Value;

/**
 * A world clock as the client was told it last, from Minecraft 26.1. A clock counts the ticks of what follows it, such
 * as the day of the overworld; the client advances it itself between updates.
 */
@Value
public class ClockInfo {
	/**
	 * The ticks the clock has counted.
	 */
	long ticks;
	/**
	 * How many ticks the clock counts each game tick: {@code 1} normally and {@code 0} while it is paused or the server
	 * does not advance time.
	 */
	float rate;
}
