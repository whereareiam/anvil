package me.whereareiam.anvil.protocol.java.api.model.dialog;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A button of a dialog.
 */
@Value
public class DialogButton {
	/**
	 * The button's label as plain text.
	 */
	@NotNull String label;
	/**
	 * The action the button runs, or {@code null} for a button that only closes the dialog.
	 */
	@Nullable DialogAction action;
	/**
	 * Whether the client presses this button when the player closes the dialog with Escape: the exit action of a
	 * multi-action, server-links or dialog-list dialog, the {@code no} button of a confirmation and the button of a
	 * notice.
	 */
	boolean exit;
}
