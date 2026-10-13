package me.whereareiam.anvil.capability.dialogs.model.dialog;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A button of a dialog.
 */
@Value
public class DialogButton {
	/**
	 * Button text, flattened to plain text.
	 */
	@NotNull String label;
	/**
	 * Action the button runs, or {@code null} for a button that only closes the dialog.
	 */
	@Nullable DialogAction action;
	/**
	 * Whether this is the button the client presses when the player closes the dialog with Escape: the exit button
	 * of a multi-action, server-links or dialog-list dialog, the {@code no} button of a confirmation and the only
	 * button of a notice.
	 */
	boolean exit;
}
