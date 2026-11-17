package me.whereareiam.anvil.capability.world.block;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.world.model.block.Block;
import me.whereareiam.anvil.capability.world.model.block.BlockArea;
import me.whereareiam.anvil.capability.world.model.block.BlockChange;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * The changes of an area's blocks since the watch opened, which {@link BlockView#watch(BlockArea)} creates. The watch
 * reads them through a cursor: each wait looks at the changes after the cursor, oldest first, and moves the cursor
 * past the one it returns, so two waits for the same block see two changes and a later wait never sees an earlier
 * change again. A position outside the area is refused, since the watch never sees it change.
 *
 * <p>The watch keeps its changes until it closes; closing stops the player's worker sending them.</p>
 *
 * <pre>{@code
 * try (BlockWatch watch = blocks.watch(BlockArea.of(from, to))) {
 *     door.open();
 *     watch.changed(doorPosition, BlockQuery.withProperty("open", "true"));
 *     watch.neverChanged(floorPosition, Duration.ofSeconds(1));
 * }
 * }</pre>
 */
public interface BlockWatch extends AutoCloseable {
	/**
	 * Returns the blocks the watch follows.
	 *
	 * @return the area
	 */
	@NotNull BlockArea area();

	/**
	 * Waits for a change of a block to a state the condition accepts, within {@link SimulatedPlayer#DEFAULT_TIMEOUT}.
	 *
	 * @param position the block's position, inside the area
	 * @param condition what the block must become, ideally a {@link BlockQuery}, which a failure then describes
	 * @return the block in the state the condition accepted
	 * @throws IllegalStateException when no such change arrives in time
	 * @throws IllegalArgumentException when the position lies outside the area
	 */
	default @NotNull Block changed(@NotNull BlockPosition position, @NotNull Predicate<Block> condition) {
		return changed(position, condition, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for a change of a block to a state the condition accepts.
	 *
	 * @param position the block's position, inside the area
	 * @param condition what the block must become, ideally a {@link BlockQuery}, which a failure then describes
	 * @param timeout how long to wait
	 * @return the block in the state the condition accepted
	 * @throws IllegalStateException when no such change arrives in time
	 * @throws IllegalArgumentException when the position lies outside the area
	 */
	@NotNull Block changed(
			@NotNull BlockPosition position,
			@NotNull Predicate<Block> condition,
			@NotNull Duration timeout
	);

	/**
	 * Checks that a block does not change at all for a while.
	 *
	 * @param position the block's position, inside the area
	 * @param duration how long it must keep its state
	 * @throws IllegalStateException when the block changes
	 * @throws IllegalArgumentException when the position lies outside the area
	 */
	default void neverChanged(@NotNull BlockPosition position, @NotNull Duration duration) {
		neverChanged(position, BlockQuery.any(), duration);
	}

	/**
	 * Checks that a block does not change to a state the condition accepts for a while.
	 *
	 * @param position the block's position, inside the area
	 * @param condition the states the block must not take
	 * @param duration how long to check
	 * @throws IllegalStateException when the block takes such a state
	 * @throws IllegalArgumentException when the position lies outside the area
	 */
	void neverChanged(@NotNull BlockPosition position, @NotNull Predicate<Block> condition, @NotNull Duration duration);

	/**
	 * Creates an expectation, met once the watch has an untaken change of a block to a state the condition accepts,
	 * for a player to wait on together with others.
	 *
	 * @param position the block's position, inside the area
	 * @param condition what the block must become
	 * @return the expectation
	 * @throws IllegalArgumentException when the position lies outside the area
	 */
	@NotNull Expectation expectChanged(@NotNull BlockPosition position, @NotNull Predicate<Block> condition);

	/**
	 * Returns the changes after the cursor, without waiting or moving it.
	 *
	 * @return the changes no wait has passed yet, oldest first
	 */
	@NotNull List<BlockChange> unread();

	/**
	 * Stops following the area and forgets its changes.
	 */
	@Override
	void close();
}
