package me.whereareiam.anvil.protocol.java.api.model.dialog;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.DialogActionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The click action a dialog button runs.
 */
@Value
public class DialogAction {
	/**
	 * The action's type.
	 */
	@NotNull DialogActionType type;
	/**
	 * The command, template, custom click ID, URL, clipboard text or page number the action carries, or the action
	 * type's ID for {@link DialogActionType#OTHER}. For {@link DialogActionType#SHOW_DIALOG}, the registry name of the
	 * dialog it opens with its namespace, or empty when it carries the dialog itself.
	 */
	@NotNull String value;
	/**
	 * Payload of a {@link DialogActionType#CUSTOM} action or additions of a {@link DialogActionType#DYNAMIC_CUSTOM}
	 * action as SNBT, or {@code null} when the action has none.
	 */
	@Nullable String payload;
	/**
	 * The dialog a {@link DialogActionType#SHOW_DIALOG} action carries itself, or {@code null} when it names a
	 * registry dialog or is of another type.
	 */
	@Nullable Dialog dialog;
}
