package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the boss bar changes of a player from the moment the watch was created. A bar is shown when the server
 * adds it and each time the server changes it, so {@link #shown(Predicate, Duration)} finds the first addition or
 * change after the previous wait that leaves the bar in the required state, such as a progress reaching one half.
 * Each wait continues after the change the previous one found.
 *
 * <pre>{@code
 * BossBarWatch bars = screen.watchBossBars();
 * messages.command("event start");
 * BossBar event = bars.shown(bar -> bar.getText().contains("Event") && bar.getProgress() >= 0.5F, timeout);
 * bars.removed(bar -> bar.getId().equals(event.getId()), timeout);
 * }</pre>
 */
public interface BossBarWatch {
	/**
	 * Waits, using the default timeout, for the next boss bar added or changed with a text containing the supplied
	 * text.
	 *
	 * @param text required bar text content
	 * @return the bar after that addition or change
	 */
	default @NotNull BossBar shown(@NotNull String text) {
		return shown(text, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next boss bar added or changed with a text containing the supplied text.
	 *
	 * @param text required bar text content
	 * @param timeout maximum wait
	 * @return the bar after that addition or change
	 * @throws IllegalStateException when no such change arrives in time
	 */
	default @NotNull BossBar shown(@NotNull String text, @NotNull Duration timeout) {
		return shown(bar -> bar.getText().contains(text), timeout);
	}

	/**
	 * Waits for the next boss bar addition or change after which the bar satisfies a condition. The following wait
	 * looks only at later changes.
	 *
	 * @param matcher condition on the bar after the change
	 * @param timeout maximum wait
	 * @return the bar after that addition or change
	 * @throws IllegalStateException when no such change arrives in time
	 */
	@NotNull BossBar shown(@NotNull Predicate<BossBar> matcher, @NotNull Duration timeout);

	/**
	 * Waits, using the default timeout, for the next boss bar removed while its text contained the supplied text.
	 *
	 * @param text required bar text content
	 * @return the removed bar as it was last shown
	 */
	default @NotNull BossBar removed(@NotNull String text) {
		return removed(text, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next boss bar removed while its text contained the supplied text.
	 *
	 * @param text required bar text content
	 * @param timeout maximum wait
	 * @return the removed bar as it was last shown
	 * @throws IllegalStateException when no such bar is removed in time
	 */
	default @NotNull BossBar removed(@NotNull String text, @NotNull Duration timeout) {
		return removed(bar -> bar.getText().contains(text), timeout);
	}

	/**
	 * Waits for the next boss bar that leaves the screen while satisfying a condition, because the server removed it
	 * or its connection ended. The following wait looks only at later changes.
	 *
	 * @param matcher condition on the bar as it was last shown
	 * @param timeout maximum wait
	 * @return the removed bar as it was last shown
	 * @throws IllegalStateException when no such bar is removed in time
	 */
	@NotNull BossBar removed(@NotNull Predicate<BossBar> matcher, @NotNull Duration timeout);

	/**
	 * Expects that no boss bar with a text containing the supplied text is added or changed for a duration.
	 *
	 * @param text forbidden bar text content
	 * @param duration how long no such bar may be shown
	 * @throws IllegalStateException as soon as such a bar has been shown since the previous wait
	 */
	default void neverShown(@NotNull String text, @NotNull Duration duration) {
		neverShown(bar -> bar.getText().contains(text), duration);
	}

	/**
	 * Expects that no boss bar addition or change leaves a bar satisfying a condition for a duration. It fails as
	 * soon as such a change has arrived since the previous wait, naming the bar's text, and returns normally once the
	 * duration has passed without one. It does not move the watch.
	 *
	 * @param matcher condition on the bar after the change
	 * @param duration how long no matching bar may be shown
	 * @throws IllegalStateException when such a bar is shown
	 */
	void neverShown(@NotNull Predicate<BossBar> matcher, @NotNull Duration duration);

	/**
	 * Expects the next boss bar addition or change that leaves a bar satisfying a condition, for
	 * {@link SimulatedPlayer#awaitFirst}. Once it is met, {@link #shown(Predicate, Duration)} with the same condition
	 * returns the bar at once.
	 *
	 * @param matcher condition on the bar after the change
	 * @return expectation met once such a change has arrived since the previous wait
	 */
	@NotNull Expectation expectShown(@NotNull Predicate<BossBar> matcher);

	/**
	 * Expects the next removal of a boss bar satisfying a condition, for {@link SimulatedPlayer#awaitFirst}. Once it
	 * is met, {@link #removed(Predicate, Duration)} with the same condition returns the bar at once.
	 *
	 * @param matcher condition on the bar as it was last shown
	 * @return expectation met once such a bar has been removed since the previous wait
	 */
	@NotNull Expectation expectRemoved(@NotNull Predicate<BossBar> matcher);

	/**
	 * Returns the boss bar changes since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread changes, oldest first
	 */
	@NotNull List<BossBarEvent> unread();
}
