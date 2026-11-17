package me.whereareiam.anvil.capability.world.channel;

import lombok.Value;
import me.whereareiam.anvil.capability.world.model.level.ChunkPosition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The chunk columns the player's client has.
 */
@Value
public class LoadedChunks {
	/**
	 * The columns' positions, in no particular order.
	 */
	@NotNull List<ChunkPosition> chunks;
}
