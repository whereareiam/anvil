package me.whereareiam.anvil.protocol.java.api.model.world;

import lombok.Value;

/**
 * The state of a world clock the server sends from Minecraft 26.1, from which the client advances the clock itself
 * until the next update.
 */
@Value
public class ClockState {
	/**
	 * The ticks the clock has counted.
	 */
	long totalTicks;
	/**
	 * The fraction of the next tick the clock has counted.
	 */
	float partialTick;
	/**
	 * How many ticks the clock counts each game tick: {@code 0} while it is paused or the server does not advance time.
	 */
	float rate;
}
