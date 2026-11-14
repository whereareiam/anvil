package me.whereareiam.anvil.capability.vitals;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.vitals.model.Death;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the deaths of a player from the moment the watch was created. Each wait continues after the death the
 * previous one found, so a death is matched once per wait and never by a death from before the watch.
 *
 * <pre>{@code
 * DeathWatch deaths = vitals.watchDeaths();
 * messages.command("arena start");
 * deaths.died(death -> death.getMessage().contains("fell"), Duration.ofSeconds(10));
 * deaths.neverDied(Duration.ofSeconds(5));
 * }</pre>
 */
public interface DeathWatch {
	/**
	 * Waits, using the default timeout, for the player to die.
	 *
	 * @return the death
	 */
	default @NotNull Death died() {
		return died(SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the player to die.
	 *
	 * @param timeout maximum wait
	 * @return the death
	 * @throws IllegalStateException when the player does not die in time
	 */
	default @NotNull Death died(@NotNull Duration timeout) {
		return died(death -> true, timeout);
	}

	/**
	 * Waits for the next death that satisfies a condition. The following wait looks only at later deaths.
	 *
	 * @param matcher condition on the death
	 * @param timeout maximum wait
	 * @return the matching death
	 * @throws IllegalStateException when no such death happens in time
	 */
	@NotNull Death died(@NotNull Predicate<Death> matcher, @NotNull Duration timeout);

	/**
	 * Expects that the player does not die for a duration.
	 *
	 * @param duration how long the player may not die
	 * @throws IllegalStateException as soon as the player has died since the previous wait
	 */
	default void neverDied(@NotNull Duration duration) {
		neverDied(death -> true, duration);
	}

	/**
	 * Expects that no death satisfying a condition happens for a duration. It fails as soon as such a death has
	 * happened since the previous wait, naming its message, and returns normally once the duration has passed without
	 * one. It does not move the watch.
	 *
	 * @param matcher condition on the death
	 * @param duration how long no matching death may happen
	 * @throws IllegalStateException when such a death happens
	 */
	void neverDied(@NotNull Predicate<Death> matcher, @NotNull Duration duration);

	/**
	 * Expects the next death that satisfies a condition, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #died(Predicate, Duration)} with the same condition returns the death at once.
	 *
	 * @param matcher condition on the death
	 * @return expectation met once such a death has happened since the previous wait
	 */
	@NotNull Expectation expectDied(@NotNull Predicate<Death> matcher);

	/**
	 * Returns the deaths since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread deaths, oldest first
	 */
	@NotNull List<Death> unread();
}
