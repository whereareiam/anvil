package me.whereareiam.anvil.capability.movement.model;

import lombok.Value;

/**
 * A player's velocity in blocks per tick, as the client's physics keeps it.
 */
@Value
public class Motion {
	/**
	 * The east-west velocity.
	 */
	double x;
	/**
	 * The vertical velocity; negative while falling.
	 */
	double y;
	/**
	 * The north-south velocity.
	 */
	double z;
}
