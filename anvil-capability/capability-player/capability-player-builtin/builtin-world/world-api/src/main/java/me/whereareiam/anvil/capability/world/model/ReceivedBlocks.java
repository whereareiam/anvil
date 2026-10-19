package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The blocks one block update packet set, in a column the client has, as the worker reports them.
 */
@Value
public class ReceivedBlocks {
	/**
	 * Number of the player's connection that received the update, starting at 1.
	 */
	int connection;
	/**
	 * The key of the level the player was in, such as {@code minecraft:overworld}.
	 */
	@NotNull String dimension;
	/**
	 * The blocks the update set, in the order the server sent them.
	 */
	@NotNull List<ReceivedBlock> blocks;
}
