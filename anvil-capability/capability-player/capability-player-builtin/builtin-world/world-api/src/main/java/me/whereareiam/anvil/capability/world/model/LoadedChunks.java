package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The chunk columns the player's client has, as the worker answers a read of them.
 */
@Value
public class LoadedChunks {
	/**
	 * The loaded columns, in no particular order.
	 */
	@NotNull List<SeenChunk> chunks;
}
