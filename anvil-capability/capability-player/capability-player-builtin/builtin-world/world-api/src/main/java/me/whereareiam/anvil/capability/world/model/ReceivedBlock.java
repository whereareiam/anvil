package me.whereareiam.anvil.capability.world.model;

import lombok.Value;

/**
 * One block a block update set, as the worker reports it before the host names its state.
 */
@Value
public class ReceivedBlock {
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
	/**
	 * The global ID of the state the update set, in the player's release.
	 */
	int stateId;
}
