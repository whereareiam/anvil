package me.whereareiam.anvil.protocol.java.api.model.dialog;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.DialogAfterAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A dialog as the game's dialog codec describes it, with its text as plain text and the buttons of every dialog type
 * as one list. Missing optional fields carry the game's defaults.
 */
@Value
@Builder
public class Dialog {
	/**
	 * Dialog type ID with its namespace, such as {@code minecraft:multi_action} or {@code minecraft:notice}.
	 */
	@NotNull String type;
	/**
	 * The title.
	 */
	@NotNull String title;
	/**
	 * Title a dialog-list dialog shows on the button that opens this dialog; the title when the dialog has none.
	 */
	@NotNull String externalTitle;
	/**
	 * Body elements in order: the text of each message and the description of each item, empty for an item without
	 * one.
	 */
	@NotNull @Singular("body") List<String> body;
	/**
	 * Inputs in display order.
	 */
	@NotNull @Singular List<DialogInput> inputs;
	/**
	 * Buttons in display order, the exit button last: the actions and exit action of a multi-action or server-links
	 * dialog, one button per listed dialog and the exit action of a dialog list, {@code yes} and {@code no} of a
	 * confirmation, and the action of a notice, which is a {@code gui.ok} button without an action by default.
	 */
	@NotNull @Singular List<DialogButton> buttons;
	/**
	 * Whether the player may close the dialog with Escape; {@code true} by default.
	 */
	boolean closeWithEscape;
	/**
	 * What the client does with the dialog after a button ran its action; {@link DialogAfterAction#CLOSE} by default.
	 */
	@NotNull DialogAfterAction afterAction;
}
