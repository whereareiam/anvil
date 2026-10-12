package me.whereareiam.anvil.capability.dialogs.model.dialog;

import lombok.Value;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The action a dialog button runs.
 */
@Value
public class DialogAction {
	@NotNull DialogActionType type;
	/**
	 * The command, template, custom click ID, URL, clipboard text or page the action carries, or the action type's ID
	 * for {@link DialogActionType#OTHER}; for {@link DialogActionType#SHOW_DIALOG}, the registry name of the dialog it
	 * opens, or empty when it carries the dialog itself.
	 */
	@NotNull String value;
	/**
	 * Payload of a {@link DialogActionType#CUSTOM} action or additions of a {@link DialogActionType#DYNAMIC_CUSTOM}
	 * action, as SNBT, or {@code null} when the action has none.
	 */
	@Nullable String payload;
	/**
	 * Dialog a {@link DialogActionType#SHOW_DIALOG} action opens, or {@code null} when the action names a registry
	 * dialog Anvil does not know or is of another type.
	 */
	@Nullable Dialog dialog;

	/**
	 * Creates an action that carries no dialog.
	 *
	 * @param type action type
	 * @param value the command, template, ID, URL, text or page the action carries
	 * @param payload SNBT payload or additions, or {@code null}
	 * @return the action
	 */
	public static @NotNull DialogAction of(@NotNull DialogActionType type, @NotNull String value, @Nullable String payload) {
		return new DialogAction(type, value, payload, null);
	}

	/**
	 * Creates an action that opens a dialog.
	 *
	 * @param registryName registry name of the dialog, or empty when the action carries the dialog itself
	 * @param dialog the dialog, or {@code null} when it is not known
	 * @return the action
	 */
	public static @NotNull DialogAction show(@NotNull String registryName, @Nullable Dialog dialog) {
		return new DialogAction(DialogActionType.SHOW_DIALOG, registryName, null, dialog);
	}
}
