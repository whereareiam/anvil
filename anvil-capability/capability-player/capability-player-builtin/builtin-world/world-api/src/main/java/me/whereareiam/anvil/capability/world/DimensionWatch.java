package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the dimension changes of a player from the moment the watch was created. Each wait continues after the
 * change the previous one found, so a dimension entered again is matched once per wait and never by a change from
 * before the watch.
 *
 * <p>A dimension is named by its level's key. A key without a namespace, such as {@code the_nether}, is in the
 * {@code minecraft} namespace, as in the game's commands.</p>
 *
 * <pre>{@code
 * DimensionWatch dimensions = world.watch();
 * messages.command("warp arena");
 * dimensions.entered("minecraft:arena");
 * dimensions.neverEntered("minecraft:overworld", Duration.ofSeconds(2));
 * }</pre>
 */
public interface DimensionWatch {
	/**
	 * Waits, using the default timeout, for the player to enter a dimension.
	 *
	 * @param dimension key of the dimension's level
	 * @return the change into it
	 */
	default @NotNull DimensionChange entered(@NotNull String dimension) {
		return entered(dimension, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the player to enter a dimension: to join a level of that dimension, or to respawn in it from another
	 * one. A respawn in the dimension the player was already in does not enter it.
	 *
	 * @param dimension key of the dimension's level
	 * @param timeout maximum wait
	 * @return the change into it
	 * @throws IllegalStateException when the player does not enter the dimension in time
	 */
	default @NotNull DimensionChange entered(@NotNull String dimension, @NotNull Duration timeout) {
		return changed(entering(dimension), timeout);
	}

	/**
	 * Waits for the next dimension change that satisfies a condition. The following wait looks only at later changes.
	 *
	 * @param matcher condition on the change
	 * @param timeout maximum wait
	 * @return the matching change
	 * @throws IllegalStateException when no such change happens in time
	 */
	@NotNull DimensionChange changed(@NotNull Predicate<DimensionChange> matcher, @NotNull Duration timeout);

	/**
	 * Expects that the player does not enter a dimension for a duration.
	 *
	 * @param dimension key of the forbidden dimension's level
	 * @param duration how long the player may not enter it
	 * @throws IllegalStateException as soon as the player has entered it since the previous wait
	 */
	default void neverEntered(@NotNull String dimension, @NotNull Duration duration) {
		neverChanged(entering(dimension), duration);
	}

	/**
	 * Expects that no dimension change satisfying a condition happens for a duration. It fails as soon as such a
	 * change has happened since the previous wait, naming it, and returns normally once the duration has passed
	 * without one. It does not move the watch.
	 *
	 * @param matcher condition on the change
	 * @param duration how long no matching change may happen
	 * @throws IllegalStateException when such a change happens
	 */
	void neverChanged(@NotNull Predicate<DimensionChange> matcher, @NotNull Duration duration);

	/**
	 * Expects the next dimension change that satisfies a condition, for {@link SimulatedPlayer#awaitFirst}. Once it
	 * is met, {@link #changed(Predicate, Duration)} with the same condition returns the change at once.
	 *
	 * @param matcher condition on the change
	 * @return expectation met once such a change has happened since the previous wait
	 */
	@NotNull Expectation expectChanged(@NotNull Predicate<DimensionChange> matcher);

	/**
	 * Returns the dimension changes since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread changes, oldest first
	 */
	@NotNull List<DimensionChange> unread();

	/**
	 * Matches a change into a dimension from another one or from no level.
	 *
	 * @param dimension key of the dimension's level, with or without its namespace
	 * @return condition on a change
	 */
	static @NotNull Predicate<DimensionChange> entering(@NotNull String dimension) {
		String key = dimension.indexOf(':') < 0 ? "minecraft:" + dimension : dimension;
		return change -> change.getTo().equals(key) && !key.equals(change.getFrom());
	}
}
