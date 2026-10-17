package me.whereareiam.anvil.capability.dialogs.model.dialog;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A dialog the server showed to the player, with its text flattened to plain text: the content of each text part,
 * the key name of each keybind part, the pattern of each selector part and, for a translated part, its translation
 * key followed by each argument after a space, so a default button reads {@code gui.ok}.
 *
 * <p>The server may refer to a dialog of its dialog registry instead of sending it, such as one from a data pack.
 * Anvil resolves the reference from the registry the server sent during configuration. A dialog the server did not
 * send because the client knows it, such as a built-in one, carries only its registry ID and name: its title, body,
 * inputs and buttons are empty.</p>
 */
@Value
@Builder(toBuilder = true)
public class Dialog {
	/**
	 * Dialog type ID, such as {@code minecraft:multi_action} or {@code minecraft:notice}, or empty for a dialog
	 * shown by registry ID.
	 */
	@NotNull String type;
	/**
	 * Registry ID of a dialog the server referred to instead of sending it, or {@code null} for a dialog sent whole.
	 */
	@Nullable Integer registryId;
	/**
	 * Registry name of a dialog that comes from the dialog registry, such as {@code example:rules}, or {@code null}
	 * for a dialog sent whole or one Anvil could not find in the registry.
	 */
	@Nullable String registryName;
	@NotNull String title;
	/**
	 * Title a dialog-list dialog shows on the button that opens this dialog; the title when the dialog has none.
	 */
	@NotNull String externalTitle;
	/**
	 * Body elements in order: the text of each message and the description of each item.
	 */
	@NotNull @Singular("body") List<String> body;
	@NotNull @Singular List<DialogInput> inputs;
	/**
	 * Buttons in display order, the exit button last.
	 */
	@NotNull @Singular List<DialogButton> buttons;
	/**
	 * Whether the player may close the dialog with Escape.
	 */
	boolean closeWithEscape;
	@NotNull DialogAfterAction afterAction;
	/**
	 * Whether the server showed the dialog during the configuration phase, where the client runs no commands.
	 */
	boolean configuration;
}
