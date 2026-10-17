package me.whereareiam.anvil.capability.dialogs;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.dialogs.model.click.CommandConfirmation;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Observes the dialogs the server shows a player and presses their buttons as the Minecraft client does. Dialogs
 * exist from Minecraft 1.21.6; players of older releases do not have this capability.
 *
 * <p>The capability follows the client's screens. A dialog the server shows replaces the open dialog and keeps the
 * screen below it, and clearing a dialog returns to that screen. A button runs its action and then, as its
 * after-action says, keeps the dialog open, closes it, or shows a waiting screen until the server answers. A command
 * the client cannot check opens a confirmation screen first. Every dialog closes when the client joins a level,
 * respawns, changes servers behind a proxy, starts configuring again or reconnects. The history holds every dialog
 * the player is shown, in order and across reconnects.</p>
 *
 * <pre>{@code
 * Dialogs dialogs = alice.capability(Dialogs.class);
 * dialogs.shown("Log in");
 * dialogs.press("Log in", Map.of("password", "Secret123!"));
 * dialogs.closed(Duration.ofSeconds(5));
 * }</pre>
 */
public interface Dialogs extends PlayerCapability {
	/**
	 * Returns the dialog open now without waiting.
	 *
	 * @return the dialog on screen, or empty when the player sees another screen or none
	 */
	@NotNull Optional<Dialog> current();

	/**
	 * Returns the confirmation screen on which the client asks the player whether to run a command, without
	 * waiting.
	 *
	 * @return the confirmation on screen, or empty when none is
	 */
	@NotNull Optional<CommandConfirmation> confirmation();

	/**
	 * Returns whether the client shows the waiting screen of a dialog whose button waits for the server's response.
	 *
	 * @return whether the waiting screen is on screen
	 */
	boolean waitingForResponse();

	/**
	 * Returns an immutable snapshot of every dialog the player was shown, including dialogs opened by a button, without
	 * waiting.
	 *
	 * @return shown dialogs in order
	 */
	@NotNull List<Dialog> history();

	/**
	 * Waits until the open dialog's title contains the supplied text, using the default timeout.
	 *
	 * @param title required title content
	 * @return the open dialog
	 */
	default @NotNull Dialog shown(@NotNull String title) {
		return shown(title, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until the open dialog's title contains the supplied text.
	 *
	 * @param title required title content
	 * @param timeout maximum wait
	 * @return the open dialog
	 */
	@NotNull Dialog shown(@NotNull String title, @NotNull Duration timeout);

	/**
	 * Starts following the dialogs the player is shown from now on. Each watch keeps its own position, so several can
	 * follow the same player independently.
	 *
	 * <pre>{@code
	 * DialogWatch shown = dialogs.watch();
	 * dialogs.press("Log in", Map.of("password", "wrong"));
	 * Dialog again = shown.shown(dialog -> dialog.getBody().contains("Invalid password."), timeout);
	 * }</pre>
	 *
	 * @return a watch that starts after the last dialog shown so far
	 */
	@NotNull DialogWatch watch();

	/**
	 * Waits until no dialog is on screen.
	 *
	 * @param timeout maximum wait
	 */
	void closed(@NotNull Duration timeout);

	/**
	 * Presses a button of the open dialog with every input at its initial value.
	 *
	 * @param button the button's label, matched exactly
	 * @throws IllegalStateException when no dialog is open, it has no such button, or the button runs a command and
	 *                               the player has no Commands capability
	 */
	default void press(@NotNull String button) {
		press(button, Map.of());
	}

	/**
	 * Fills inputs of the open dialog and presses one of its buttons, doing what the client does for the button's
	 * action:
	 *
	 * <ul>
	 *     <li>{@code run_command} runs its command, and {@code dynamic/run_command} runs its template with each
	 *     {@code $(key)} replaced: by the text of a text input, the ID of the selected option, the number of a slider
	 *     (without a fraction when it is whole) or a checkbox's {@code on_true} or {@code on_false} text. The client
	 *     first checks the command against the server's command tree and sends it only when it parses and needs no
	 *     permissions; otherwise it shows a {@link #confirmation()} screen. The check needs the player's
	 *     {@link me.whereareiam.anvil.capability.commands.Commands} capability. During the configuration phase the
	 *     client runs no commands, and the dialog stays as it is;</li>
	 *     <li>{@code custom} sends its ID and payload, and {@code dynamic/custom} sends its ID with every input value
	 *     put into its additions; the action must still belong to a dialog the server shows, or to its dialog
	 *     registry;</li>
	 *     <li>{@code show_dialog} opens its dialog in place of this one;</li>
	 *     <li>a button without an action, {@code suggest_command}, {@code open_url}, {@code copy_to_clipboard} and
	 *     {@code change_page} send nothing.</li>
	 * </ul>
	 *
	 * <p>Afterwards the dialog stays open, closes or gives way to the waiting screen, as its after-action says.</p>
	 *
	 * @param button the button's label, matched exactly
	 * @param inputs values by input key, written as {@link me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput#getInitial()}
	 *               writes them; inputs left out keep their initial value
	 * @throws IllegalStateException when no dialog is open or it has no such button, or when the server replaced or
	 *                               cleared the dialog of a pressed custom action before its click was sent
	 * @throws IllegalArgumentException when an input does not exist or does not accept its value, a template names an
	 *                                  input the dialog does not have, or the button opens a dialog Anvil does not know
	 */
	void press(@NotNull String button, @NotNull Map<String, String> inputs);

	/**
	 * Presses Escape. On a dialog the client presses the dialog's exit button, if it has one, with every input at its
	 * initial value, and closes the dialog. On a confirmation screen it declines. On the waiting screen it returns to
	 * the screen below, which the client allows five seconds after the screen appeared.
	 *
	 * @throws IllegalStateException when no screen is open, the dialog cannot be closed with Escape, or the waiting
	 *                               screen does not allow it yet
	 */
	void close();

	/**
	 * Confirms the open confirmation screen. The client then sends the command, except one with a signed argument,
	 * which it only puts into the chat input or the clipboard, and shows the screen the button's after-action leads to.
	 *
	 * @throws IllegalStateException when no confirmation screen is open
	 */
	void confirm();

	/**
	 * Declines the open confirmation screen, which returns to the dialog without running the command.
	 *
	 * @throws IllegalStateException when no confirmation screen is open
	 */
	void decline();
}
