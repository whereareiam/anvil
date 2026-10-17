package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the action bars shown to a player from the moment the watch was created. Each wait continues after the
 * action bar the previous one found, so a text shown again, as action bars often are to keep them visible, is
 * matched once per wait and never by an occurrence from before the watch.
 *
 * <pre>{@code
 * ActionBarWatch bar = screen.watchActionBars();
 * messages.command("follow Bob");
 * bar.shown("+1 follower");
 * bar.neverShown("-1 follower", Duration.ofSeconds(2));
 * }</pre>
 */
public interface ActionBarWatch {
	/**
	 * Waits, using the default timeout, for the next action bar containing the supplied text.
	 *
	 * @param text required action-bar content
	 * @return the matching action bar
	 */
	default @NotNull String shown(@NotNull String text) {
		return shown(text, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next action bar containing the supplied text.
	 *
	 * @param text required action-bar content
	 * @param timeout maximum wait
	 * @return the matching action bar
	 * @throws IllegalStateException when no such action bar is shown in time
	 */
	default @NotNull String shown(@NotNull String text, @NotNull Duration timeout) {
		return shown(actionBar -> actionBar.contains(text), timeout);
	}

	/**
	 * Waits for the next action bar that satisfies a condition. The following wait looks only at later action bars.
	 *
	 * @param matcher condition on the action bar's plain text
	 * @param timeout maximum wait
	 * @return the matching action bar
	 * @throws IllegalStateException when no such action bar is shown in time
	 */
	@NotNull String shown(@NotNull Predicate<String> matcher, @NotNull Duration timeout);

	/**
	 * Expects that no action bar containing the supplied text is shown for a duration.
	 *
	 * @param text forbidden action-bar content
	 * @param duration how long no such action bar may be shown
	 * @throws IllegalStateException as soon as such an action bar has been shown since the previous wait
	 */
	default void neverShown(@NotNull String text, @NotNull Duration duration) {
		neverShown(actionBar -> actionBar.contains(text), duration);
	}

	/**
	 * Expects that no action bar satisfying a condition is shown for a duration. It fails as soon as such an action
	 * bar has been shown since the previous wait, naming it, and returns normally once the duration has passed
	 * without one. It does not move the watch.
	 *
	 * @param matcher condition on the action bar's plain text
	 * @param duration how long no matching action bar may be shown
	 * @throws IllegalStateException when such an action bar is shown
	 */
	void neverShown(@NotNull Predicate<String> matcher, @NotNull Duration duration);

	/**
	 * Expects the next action bar that satisfies a condition, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #shown(Predicate, Duration)} with the same condition returns the action bar at once.
	 *
	 * @param matcher condition on the action bar's plain text
	 * @return expectation met once such an action bar has been shown since the previous wait
	 */
	@NotNull Expectation expectShown(@NotNull Predicate<String> matcher);

	/**
	 * Returns the action bars shown since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread action bars, oldest first
	 */
	@NotNull List<String> unread();
}
