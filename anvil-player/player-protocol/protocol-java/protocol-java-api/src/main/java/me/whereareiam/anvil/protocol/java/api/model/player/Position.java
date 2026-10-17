package me.whereareiam.anvil.protocol.java.api.model.player;

import lombok.Builder;
import lombok.Value;

/**
 * An absolute position in the player's world, in blocks, as movement packets carry it.
 */
@Value
@Builder
public class Position {
	/**
	 * The east-west coordinate.
	 */
	double x;
	/**
	 * The height of the player's feet.
	 */
	double y;
	/**
	 * The north-south coordinate.
	 */
	double z;
}
