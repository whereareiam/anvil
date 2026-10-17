package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.screen.model.Title;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the titles shown to a player from the moment the watch was created. Each wait continues after the title
 * the previous one found, so a title shown again is matched once per wait and never by an occurrence from before
 * the watch.
 *
 * <pre>{@code
 * TitleWatch titles = screen.watchTitles();
 * messages.command("example announce");
 * Title title = titles.shown("Announcement");
 * titles.neverShown("Error", Duration.ofSeconds(2));
 * }</pre>
 */
public interface TitleWatch {
	/**
	 * Waits, using the default timeout, for the next title whose title or subtitle contains the supplied text.
	 *
	 * @param text required content of the title or the subtitle
	 * @return the matching title
	 */
	default @NotNull Title shown(@NotNull String text) {
		return shown(text, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next title whose title or subtitle contains the supplied text.
	 *
	 * @param text required content of the title or the subtitle
	 * @param timeout maximum wait
	 * @return the matching title
	 * @throws IllegalStateException when no such title is shown in time
	 */
	default @NotNull Title shown(@NotNull String text, @NotNull Duration timeout) {
		return shown(title -> title.contains(text), timeout);
	}

	/**
	 * Waits for the next title that satisfies a condition. The following wait looks only at later titles.
	 *
	 * @param matcher condition on the title
	 * @param timeout maximum wait
	 * @return the matching title
	 * @throws IllegalStateException when no such title is shown in time
	 */
	@NotNull Title shown(@NotNull Predicate<Title> matcher, @NotNull Duration timeout);

	/**
	 * Expects that no title whose title or subtitle contains the supplied text is shown for a duration.
	 *
	 * @param text forbidden content of the title or the subtitle
	 * @param duration how long no such title may be shown
	 * @throws IllegalStateException as soon as such a title has been shown since the previous wait
	 */
	default void neverShown(@NotNull String text, @NotNull Duration duration) {
		neverShown(title -> title.contains(text), duration);
	}

	/**
	 * Expects that no title satisfying a condition is shown for a duration. It fails as soon as such a title has been
	 * shown since the previous wait, naming it, and returns normally once the duration has passed without one. It
	 * does not move the watch.
	 *
	 * @param matcher condition on the title
	 * @param duration how long no matching title may be shown
	 * @throws IllegalStateException when such a title is shown
	 */
	void neverShown(@NotNull Predicate<Title> matcher, @NotNull Duration duration);

	/**
	 * Expects the next title that satisfies a condition, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #shown(Predicate, Duration)} with the same condition returns the title at once.
	 *
	 * @param matcher condition on the title
	 * @return expectation met once such a title has been shown since the previous wait
	 */
	@NotNull Expectation expectShown(@NotNull Predicate<Title> matcher);

	/**
	 * Returns the titles shown since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread titles, oldest first
	 */
	@NotNull List<Title> unread();
}
