package me.whereareiam.anvil.capability.world.channel;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The blocks of one watched area that one packet changed, in the order the packet listed them.
 */
@Value
public class WatchedChanges {
	/**
	 * The ID of the watch whose area holds the blocks.
	 */
	int watch;
	/**
	 * The key of the level the blocks are in.
	 */
	@NotNull String level;
	/**
	 * The blocks in their new states.
	 */
	@NotNull List<BlockReading> blocks;
}
