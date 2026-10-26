package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import me.whereareiam.anvil.capability.world.model.SeenChunk;
import me.whereareiam.anvil.capability.world.model.SeenDimension;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The world as the player's client knows it: the level it is in, the chunk columns the server sent it, and the block
 * at each position of those columns, kept up to date by the block updates that followed.
 *
 * <p>A block is known while the client has its chunk column; outside the loaded columns, and above or below the
 * level, the client knows nothing, and {@link #block} says so instead of guessing. Every known block has the global
 * ID of its state in the player's release. Its name and properties come from the block data Anvil ships for that
 * release; for a release Anvil has no block data for, {@link #knowsBlockNames()} is false and every block is known by
 * its state ID only.</p>
 *
 * <p>The world follows the player's current connection: it is empty before the first login, a respawn into another
 * level empties it, and so do a return to the configuration phase and the end of the connection.</p>
 *
 * <pre>{@code
 * World world = player.capability(World.class);
 * WorldWatch blocks = world.watch();
 * server.console("setblock 10 64 10 minecraft:gold_block");
 * SeenBlock gold = blocks.changed(10, 64, 10, BlockQuery.named("gold_block"));
 * SeenBlock below = world.awaitBlock(10, 63, 10, BlockQuery.any());
 * }</pre>
 */
public interface World extends PlayerCapability {
	/**
	 * Returns the level the player is in, without waiting.
	 *
	 * @return the level, or nothing before the first login of the current connection, while the player configures
	 * and once its connection ended
	 */
	@NotNull Optional<SeenDimension> dimension();

	/**
	 * Returns the block at a position as the client knows it now, without waiting.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @return the block, or nothing when the client does not have its chunk column or the height lies outside the
	 * level
	 */
	@NotNull Optional<SeenBlock> block(int x, int y, int z);

	/**
	 * Waits, using the default timeout, until the client knows the block at a position and it satisfies a condition.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @param matcher condition on the block as it is now
	 * @return the block as it was when the wait found it
	 * @throws IllegalStateException when the block is not known or does not satisfy the condition in time
	 */
	default @NotNull SeenBlock awaitBlock(int x, int y, int z, @NotNull Predicate<SeenBlock> matcher) {
		return awaitBlock(x, y, z, matcher, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until the client knows the block at a position and it satisfies a condition. Unlike a watch, it reads the
	 * current block on every check, so it also sees a block that arrives with its chunk column, and it returns at
	 * once for a block that already satisfies the condition.
	 *
	 * <pre>{@code
	 * SeenBlock floor = world.awaitBlock(0, 63, 0, BlockQuery.named("stone"), Duration.ofSeconds(5));
	 * }</pre>
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @param matcher condition on the block as it is now
	 * @param timeout maximum wait
	 * @return the block as it was when the wait found it
	 * @throws IllegalStateException when the block is not known or does not satisfy the condition in time
	 * @throws UnknownBlockNameException when the condition asks for a
	 * block's name or properties and Anvil has no block data for the player's release
	 */
	@NotNull SeenBlock awaitBlock(
			int x,
			int y,
			int z,
			@NotNull Predicate<SeenBlock> matcher,
			@NotNull Duration timeout
	);

	/**
	 * Tells whether the client has a chunk column now, without waiting.
	 *
	 * @param chunkX the column's east-west chunk coordinate, the block coordinate divided by 16 and rounded down
	 * @param chunkZ the column's north-south chunk coordinate, the block coordinate divided by 16 and rounded down
	 * @return whether the server sent the column and has not told the client to forget it since
	 */
	boolean isChunkLoaded(int chunkX, int chunkZ);

	/**
	 * Returns the chunk columns the client has now, without waiting.
	 *
	 * @return an immutable snapshot of the loaded columns
	 */
	@NotNull Set<SeenChunk> loadedChunks();

	/**
	 * Tells whether Anvil ships block data for the player's Minecraft release, so that every known block has its name
	 * and properties. When it does not, blocks are known by their state IDs only.
	 *
	 * @return whether known blocks carry names and properties
	 */
	boolean knowsBlockNames();

	/**
	 * Starts following the block updates the player receives from now on. Each watch keeps its own position, so
	 * several can follow the same player independently.
	 *
	 * @return a watch that starts after the last block update received so far
	 */
	@NotNull WorldWatch watch();
}
