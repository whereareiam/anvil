package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import me.whereareiam.anvil.capability.world.model.BlockChange;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the block updates a player receives from the moment the watch was created.
 *
 * <p>A watch follows the updates the server sends for single blocks and for blocks of one chunk section, as a
 * {@code setblock} or {@code fill} command, a placed or broken block or an opened door causes them. Each update
 * carries the block as it was set; the client does not tell what it replaced. A chunk column that arrives or is
 * resent is no update, so a block that only arrives with its column does not change for a watch; wait for it with
 * {@link World#awaitBlock} instead. Updates of a column the client does not have are not applied, as the client
 * ignores them, and are not followed either.</p>
 *
 * <p>Each {@link #changed} wait continues after the update the previous one found, so several updates of the same
 * block are found one per wait. Conditions may be any predicate; a {@link BlockQuery} also describes itself in the
 * failure of a wait.</p>
 *
 * <pre>{@code
 * WorldWatch blocks = world.watch();
 * server.console("setblock 10 64 10 minecraft:gold_block");
 * SeenBlock gold = blocks.changed(10, 64, 10, BlockQuery.named("gold_block"));
 * blocks.unchanged(10, 64, 10, Duration.ofSeconds(2));
 * }</pre>
 */
public interface WorldWatch {
	/**
	 * Waits, using the default timeout, for the next update of the block at a position that sets it to a block
	 * satisfying a condition.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @param matcher condition on the block as the update set it
	 * @return the block as the update set it
	 * @throws IllegalStateException when no such update arrives in time
	 */
	default @NotNull SeenBlock changed(int x, int y, int z, @NotNull Predicate<SeenBlock> matcher) {
		return changed(x, y, z, matcher, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next update of the block at a position that sets it to a block satisfying a condition. Updates of
	 * the position that do not satisfy it are passed over; the following wait looks only at later updates.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @param matcher condition on the block as the update set it
	 * @param timeout maximum wait
	 * @return the block as the update set it
	 * @throws IllegalStateException when no such update arrives in time
	 * @throws UnknownBlockNameException when the condition asks for a
	 * block's name or properties and Anvil has no block data for the player's release
	 */
	@NotNull SeenBlock changed(int x, int y, int z, @NotNull Predicate<SeenBlock> matcher, @NotNull Duration timeout);

	/**
	 * Expects that the block at a position receives no update for a duration. It fails as soon as an update of the
	 * position has arrived since the previous wait, naming the block it set, and returns normally once the duration
	 * has passed without one. It does not move the watch.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @param duration how long the block may not change
	 * @throws IllegalStateException when the block receives an update
	 */
	void unchanged(int x, int y, int z, @NotNull Duration duration);

	/**
	 * Expects the next update of the block at a position that sets it to a block satisfying a condition, for
	 * {@link SimulatedPlayer#awaitFirst}. Once it is met, {@link #changed(int, int, int, Predicate, Duration)} with
	 * the same position and condition returns the block at once.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @param matcher condition on the block as the update set it
	 * @return expectation met once such an update has arrived since the previous wait
	 */
	@NotNull Expectation expectChanged(int x, int y, int z, @NotNull Predicate<SeenBlock> matcher);

	/**
	 * Returns the block updates received since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread updates, oldest first
	 */
	@NotNull List<BlockChange> unread();
}
