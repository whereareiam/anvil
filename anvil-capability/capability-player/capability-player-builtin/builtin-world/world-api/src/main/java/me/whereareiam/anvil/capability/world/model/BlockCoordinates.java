package me.whereareiam.anvil.capability.world.model;

import lombok.Value;

/**
 * The position of one block, as the host asks the worker for the block there.
 */
@Value
public class BlockCoordinates {
	/**
	 * The block's east-west coordinate.
	 */
	int x;
	/**
	 * The block's height.
	 */
	int y;
	/**
	 * The block's north-south coordinate.
	 */
	int z;
}
