package me.whereareiam.anvil.api.player;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Player-scoped identity observations assembled by the running scenario.
 */
public interface PlayerObservation {
	/**
	 * Returns the latest identity immediately.
	 *
	 * @return current identity snapshot
	 */
	@NotNull PlayerIdentity identity();

	/**
	 * Waits until the current identity satisfies a condition.
	 *
	 * @param condition condition evaluated against each identity snapshot
	 * @param timeout maximum wait
	 * @return identity that satisfied the condition
	 */
	@NotNull PlayerIdentity await(
			@NotNull Predicate<PlayerIdentity> condition,
			@NotNull Duration timeout
	);
}
