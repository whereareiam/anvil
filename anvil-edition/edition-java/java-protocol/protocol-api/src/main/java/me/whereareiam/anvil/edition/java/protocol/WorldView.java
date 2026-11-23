package me.whereareiam.anvil.edition.java.protocol;

import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The world one simulated player's client knows: the level it is in and the blocks of the chunk columns the server
 * sent it. Block states are global IDs in the release's block state registry, so the release's block data tells what
 * a state is. A {@link JavaWorld} is a view whose every call sees the world at one moment; inside
 * {@link JavaWorld#read} a view sees it at one moment for the whole reader.
 */
public interface WorldView {
	/**
	 * Returns the level the player is in.
	 *
	 * @return the level, or nothing before the first login of the current connection, while the player configures and
	 * once its connection closed
	 */
	@NotNull Optional<Level> level();

	/**
	 * Tells whether the client has a chunk column.
	 *
	 * @param chunk the column's position
	 * @return whether the server sent the column since the world began storing chunks and has not told the client to
	 * forget it since
	 */
	boolean isLoaded(@NotNull ChunkPosition chunk);

	/**
	 * Returns the state of one block as the client knows it.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @return the global ID of the block's state, which is {@code 0} for air; nothing when the client does not have the
	 * block's column or the height lies outside the level
	 */
	@NotNull OptionalInt blockState(int x, int y, int z);

	/**
	 * Returns the state of one block as the client knows it.
	 *
	 * @param position the block's position
	 * @return the global ID of the block's state, which is {@code 0} for air; nothing when the client does not have the
	 * block's column or the height lies outside the level
	 */
	default @NotNull OptionalInt blockState(@NotNull BlockPosition position) {
		return blockState(position.x(), position.y(), position.z());
	}
}
