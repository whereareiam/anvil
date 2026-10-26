package me.whereareiam.anvil.protocol.java.api;

import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The world as one simulated player's client knows it: the level it is in and the blocks of the chunk columns the
 * server sent it, kept up to date by the block updates that followed. Block states are global IDs in the release's
 * block state registry, so the release's block data tells what a state is. The view follows the player's current
 * connection: a login replaces it, a respawn into a level of another key empties it, and so do a return to the
 * configuration phase and a closed connection.
 *
 * <p>A worker extension gets the view where it gets the player's protocol and may read it from any thread; packet
 * listeners see the view with the packet they receive already applied.</p>
 *
 * <pre>{@code
 * JavaWorld world = player.protocol(JavaWorld.class);
 * OptionalInt below = world.blockState(x, y - 1, z);
 * boolean known = below.isPresent();
 * }</pre>
 */
public interface JavaWorld {
	/**
	 * Returns the level the player is in.
	 *
	 * @return the level, or nothing before the first login of the current connection, while the player configures and
	 * once its connection closed
	 */
	@NotNull Optional<Dimension> dimension();

	/**
	 * Tells whether the client has a chunk column.
	 *
	 * @param chunk the column's position
	 * @return whether the server sent the column and has not told the client to forget it since
	 */
	boolean isLoaded(@NotNull ChunkPosition chunk);

	/**
	 * Returns the chunk columns the client has.
	 *
	 * @return an immutable snapshot of their positions
	 */
	@NotNull Set<ChunkPosition> loadedChunks();

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
		return blockState(position.getX(), position.getY(), position.getZ());
	}
}
