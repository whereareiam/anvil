package me.whereareiam.anvil.capability.dialogs;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the dialogs a player is shown from the moment the watch was created. Each wait continues after the dialog
 * the previous one found, so a dialog shown again, such as a login form after a wrong password, is matched once per
 * wait and never by an occurrence from before the watch. Pressing buttons stays on {@link Dialogs}.
 *
 * <pre>{@code
 * DialogWatch shown = dialogs.watch();
 * dialogs.press("Log in", Map.of("password", "wrong"));
 * shown.shown(dialog -> dialog.getBody().contains("Invalid password."), timeout);
 * }</pre>
 */
public interface DialogWatch {
	/**
	 * Waits, using the default timeout, for the next dialog whose title contains the supplied text.
	 *
	 * @param title required title content
	 * @return the matching dialog
	 */
	default @NotNull Dialog shown(@NotNull String title) {
		return shown(dialog -> dialog.getTitle().contains(title), SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next dialog that satisfies a condition. The following wait looks only at later dialogs.
	 *
	 * @param matcher condition on the dialog
	 * @param timeout maximum wait
	 * @return the matching dialog
	 * @throws IllegalStateException when no such dialog is shown in time
	 */
	@NotNull Dialog shown(@NotNull Predicate<Dialog> matcher, @NotNull Duration timeout);

	/**
	 * Expects that no dialog satisfying a condition is shown for a duration. It fails as soon as such a dialog has
	 * been shown since the previous wait, naming its title, and returns normally once the duration has passed without
	 * one. It does not move the watch.
	 *
	 * @param matcher condition on the dialog
	 * @param duration how long no matching dialog may be shown
	 * @throws IllegalStateException when such a dialog is shown
	 */
	void neverShown(@NotNull Predicate<Dialog> matcher, @NotNull Duration duration);

	/**
	 * Expects the next dialog that satisfies a condition, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #shown(Predicate, Duration)} with the same condition returns the dialog at once.
	 *
	 * @param matcher condition on the dialog
	 * @return expectation met once such a dialog has been shown since the previous wait
	 */
	@NotNull Expectation expectShown(@NotNull Predicate<Dialog> matcher);

	/**
	 * Returns the dialogs shown since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread dialogs, oldest first
	 */
	@NotNull List<Dialog> unread();
}
