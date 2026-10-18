package me.whereareiam.anvil.capability.world.model;

import lombok.Value;

/**
 * The state of one block as the worker answers a read of it, before the host names it.
 */
@Value
public class BlockReading {
	/**
	 * Whether the client knows the block: it has the block's chunk column and the height lies inside the level.
	 */
	boolean loaded;
	/**
	 * The global ID of the block's state in the player's release; {@code 0} when the block is not loaded.
	 */
	int stateId;
}
