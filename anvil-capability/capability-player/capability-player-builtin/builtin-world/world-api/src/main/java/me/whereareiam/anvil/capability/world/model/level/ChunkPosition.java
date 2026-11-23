package me.whereareiam.anvil.capability.world.model.level;

import lombok.Value;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import org.jetbrains.annotations.NotNull;

/**
 * The position of one chunk column of the player's level: the column of blocks whose x runs from {@code x * 16} to
 * {@code x * 16 + 15} and whose z runs from {@code z * 16} to {@code z * 16 + 15}, through the level's whole height.
 *
 * <pre>{@code
 * ChunkPosition chunk = ChunkPosition.containing(new BlockPosition(-1, 64, 17));
 * assertEquals(new ChunkPosition(-1, 1), chunk);
 * }</pre>
 */
@Value
public class ChunkPosition {
	/**
	 * The blocks along each horizontal edge of a chunk.
	 */
	public static final int SIZE = 16;

	int x;
	int z;

	/**
	 * Returns the chunk that holds a block.
	 *
	 * @param block the block's position
	 * @return the chunk column it lies in
	 */
	public static @NotNull ChunkPosition containing(@NotNull BlockPosition block) {
		return new ChunkPosition(Math.floorDiv(block.x(), SIZE), Math.floorDiv(block.z(), SIZE));
	}

	/**
	 * Tells whether a block lies in this chunk column, at whatever height.
	 *
	 * @param block the block's position
	 * @return whether the block lies in the column
	 */
	public boolean contains(@NotNull BlockPosition block) {
		return containing(block).equals(this);
	}
}
