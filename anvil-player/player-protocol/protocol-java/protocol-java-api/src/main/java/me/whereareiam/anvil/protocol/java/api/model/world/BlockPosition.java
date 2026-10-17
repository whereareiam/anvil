package me.whereareiam.anvil.protocol.java.api.model.world;

import lombok.Builder;
import lombok.Value;

/**
 * The integer coordinates of a block in the player's world.
 */
@Value
@Builder
public class BlockPosition {
	/**
	 * The east-west coordinate.
	 */
	int x;
	/**
	 * The height.
	 */
	int y;
	/**
	 * The north-south coordinate.
	 */
	int z;
}
