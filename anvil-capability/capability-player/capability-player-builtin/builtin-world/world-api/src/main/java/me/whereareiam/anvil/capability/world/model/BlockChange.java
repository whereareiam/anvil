package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One block update the player received: the block as the server set it, in the level the player was in.
 */
@Value
public class BlockChange {
	/**
	 * Number of the player's connection that received the update, starting at 1.
	 */
	int connection;
	/**
	 * The key of the level the update changed, such as {@code minecraft:overworld}.
	 */
	@NotNull String dimension;
	/**
	 * The block as the update set it.
	 */
	@NotNull SeenBlock block;
}
