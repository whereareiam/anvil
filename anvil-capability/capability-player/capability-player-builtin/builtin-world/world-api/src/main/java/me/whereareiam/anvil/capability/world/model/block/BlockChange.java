package me.whereareiam.anvil.capability.world.model.block;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A block of a watched area taking a state the client did not know it in: the server updated it, or sent the chunk
 * that holds it, after the watch opened.
 */
@Value
public class BlockChange {
	/**
	 * The key of the level the block is in, such as {@code minecraft:overworld}.
	 */
	@NotNull String level;
	/**
	 * The block in its new state.
	 */
	@NotNull Block block;
}
