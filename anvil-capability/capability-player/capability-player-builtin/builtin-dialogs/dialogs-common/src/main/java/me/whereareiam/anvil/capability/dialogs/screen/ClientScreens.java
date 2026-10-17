package me.whereareiam.anvil.capability.dialogs.screen;

import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.dialogs.model.click.CommandConfirmation;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The screens of the Minecraft client that dialogs open, with the transitions the vanilla client makes between them.
 * Each screen knows the screen it returns to; {@code null} is the game without a screen. Callers synchronize on the
 * instance.
 */
public final class ClientScreens {
	/**
	 * How long the waiting screen keeps its back button disabled.
	 */
	public static final Duration WAITING_BACK_DELAY = Duration.ofSeconds(5);

	private final Clock clock;
	private @Nullable Screen screen;

	public ClientScreens(@NotNull Clock clock) {
		this.clock = clock;
	}

	/**
	 * The screens dialogs lead to.
	 */
	public sealed interface Screen permits DialogScreen, WaitingScreen, ConfirmScreen {
	}

	/**
	 * A dialog, which returns to {@code previous} when it closes.
	 */
	public record DialogScreen(Dialog dialog, @Nullable Screen previous) implements Screen {
	}

	/**
	 * The screen that waits for the server's response to a button.
	 */
	public record WaitingScreen(@Nullable Screen previous, Instant since) implements Screen {
	}

	/**
	 * The screen asking whether to run a command; confirming leads to {@code confirmed}, declining to {@code declined}.
	 */
	public record ConfirmScreen(CommandConfirmation confirmation, @Nullable Screen confirmed, @Nullable Screen declined) implements Screen {
	}

	public @Nullable Screen screen() {
		return screen;
	}

	public @Nullable Dialog dialog() {
		return screen instanceof DialogScreen open ? open.dialog() : null;
	}

	/**
	 * Shows a dialog the server sent or a button opened: it replaces an open dialog or waiting screen and returns to
	 * the screen below them.
	 *
	 * @param dialog the dialog
	 * @param from the screen the dialog is opened from
	 */
	public void show(@NotNull Dialog dialog, @Nullable Screen from) {
		Screen previous = from;
		if (from instanceof DialogScreen open) previous = open.previous();
		if (from instanceof WaitingScreen waiting) previous = waiting.previous();
		screen = new DialogScreen(dialog, previous);
	}

	/**
	 * Clears the open dialog as the server's clear packet does; any other screen stays.
	 */
	public void clear() {
		if (screen instanceof DialogScreen open) screen = open.previous();
	}

	/**
	 * Replaces every screen, as joining a level, respawning and configuring do.
	 */
	public void reset() {
		screen = null;
	}

	/**
	 * Returns the screen a button of the open dialog leads to after its action.
	 *
	 * @param open the open dialog
	 * @param afterAction what the button does with the dialog
	 * @return the open dialog, the screen below it, or a waiting screen above the screen below it
	 * @throws IllegalArgumentException for an after-action Anvil does not know
	 */
	public @Nullable Screen after(@NotNull DialogScreen open, @NotNull DialogAfterAction afterAction) {
		return switch (afterAction) {
			case NONE -> open;
			case CLOSE -> open.previous();
			case WAIT_FOR_RESPONSE -> new WaitingScreen(open.previous(), clock.instant());
			case OTHER -> throw new IllegalArgumentException("The dialog '" + open.dialog().getTitle()
					+ "' has an after-action Anvil does not know");
		};
	}

	public void set(@Nullable Screen next) {
		screen = next;
	}

	/**
	 * Opens the confirmation screen for a command the client did not send, from the screen open now.
	 *
	 * @param command the command without its leading slash
	 * @param reason why the client asks
	 * @param confirmed the screen confirming leads to
	 */
	public void confirm(@NotNull String command, @NotNull CommandCheck reason, @Nullable Screen confirmed) {
		screen = new ConfirmScreen(new CommandConfirmation(command, reason), confirmed, screen);
	}

	/**
	 * Returns whether the waiting screen's back button is enabled.
	 */
	public boolean canLeave(@NotNull WaitingScreen waiting) {
		return !clock.instant().isBefore(waiting.since().plus(WAITING_BACK_DELAY));
	}
}
