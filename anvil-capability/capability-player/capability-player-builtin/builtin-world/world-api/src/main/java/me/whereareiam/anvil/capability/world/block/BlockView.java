package me.whereareiam.anvil.capability.world.block;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.world.model.block.Block;
import me.whereareiam.anvil.capability.world.model.block.BlockArea;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.capability.world.model.level.ChunkPosition;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The blocks of the player's level as its client knows them: those of the chunk columns the server sent it, kept from
 * the moment it joins, updated by every block the server changes and forgotten with a column the server unloads or a
 * level the player leaves. A block of a column the client does not have is unknown.
 *
 * <pre>{@code
 * BlockView blocks = player.capability(World.class).blocks();
 * BlockPosition door = new BlockPosition(12, 64, -3);
 * blocks.until(door, BlockQuery.named("oak_door"));
 *
 * try (BlockWatch watch = blocks.watch(BlockArea.of(door))) {
 *     lobby.console().sendCommand("setblock 12 64 -3 minecraft:air");
 *     watch.changed(door, BlockQuery.air());
 * }
 * }</pre>
 */
public interface BlockView {
	/**
	 * Returns one block as the client knows it now, without waiting.
	 *
	 * @param position the block's position
	 * @return the block, or empty while the client has no chunk holding it or the position lies outside the level
	 */
	@NotNull Optional<Block> block(@NotNull BlockPosition position);

	/**
	 * Waits until the client knows a block in a state the condition accepts, which it may already know, within
	 * {@link SimulatedPlayer#DEFAULT_TIMEOUT}.
	 *
	 * @param position the block's position
	 * @param condition what the block must be, ideally a {@link BlockQuery}, which a failure then describes
	 * @return the first state the condition accepted
	 * @throws IllegalStateException when no accepted state arrives in time
	 */
	default @NotNull Block until(@NotNull BlockPosition position, @NotNull Predicate<Block> condition) {
		return until(position, condition, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until the client knows a block in a state the condition accepts, which it may already know. The wait
	 * watches the block while it waits, so a state the block holds only briefly still satisfies it.
	 *
	 * @param position the block's position
	 * @param condition what the block must be, ideally a {@link BlockQuery}, which a failure then describes
	 * @param timeout how long to wait
	 * @return the first state the condition accepted
	 * @throws IllegalStateException when no accepted state arrives in time
	 */
	@NotNull Block until(
			@NotNull BlockPosition position,
			@NotNull Predicate<Block> condition,
			@NotNull Duration timeout
	);

	/**
	 * Tells whether the client has a chunk column, without waiting.
	 *
	 * @param chunk the column's position
	 * @return whether the server sent the column and has not unloaded it since
	 */
	boolean isLoaded(@NotNull ChunkPosition chunk);

	/**
	 * Returns every chunk column the client has, without waiting.
	 *
	 * @return the columns' positions
	 */
	@NotNull Set<ChunkPosition> loadedChunks();

	/**
	 * Starts following the blocks of an area: from now on, every block of it that takes a state the client did not
	 * know it in, by an update or with a chunk the server sends, is a change of the watch. Only an open watch's area
	 * crosses from the player's worker, so a test pays for the blocks it watches; close the watch when done.
	 *
	 * @param area the blocks to follow
	 * @return the open watch
	 */
	@NotNull BlockWatch watch(@NotNull BlockArea area);
}
