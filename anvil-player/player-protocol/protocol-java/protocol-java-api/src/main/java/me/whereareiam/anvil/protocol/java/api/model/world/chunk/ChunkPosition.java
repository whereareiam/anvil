package me.whereareiam.anvil.protocol.java.api.model.world.chunk;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The position of a chunk column, 16 by 16 blocks wide and as high as its level, in chunk coordinates: the block
 * coordinates shifted right by four.
 *
 * <pre>{@code
 * ChunkPosition chunk = ChunkPosition.containing(-1, 17); // x -1, z 1
 * }</pre>
 */
@Value
public class ChunkPosition {
	/**
	 * The east-west chunk coordinate.
	 */
	int x;
	/**
	 * The north-south chunk coordinate.
	 */
	int z;

	/**
	 * Returns the column that holds a block.
	 *
	 * @param x the block's east-west coordinate
	 * @param z the block's north-south coordinate
	 * @return the column's position
	 */
	public static @NotNull ChunkPosition containing(int x, int z) {
		return new ChunkPosition(x >> 4, z >> 4);
	}
}
