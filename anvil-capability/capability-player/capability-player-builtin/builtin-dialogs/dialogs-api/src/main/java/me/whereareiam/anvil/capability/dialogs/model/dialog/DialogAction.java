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
	 * Handle by which a {@link DialogActionType#CUSTOM} or {@link DialogActionType#DYNAMIC_CUSTOM} action's click
	 * names it, or {@code null} for an action of another type. The player's worker gives every custom action of a
	 * dialog it was shown, or of the dialog registry, a handle unique for the player, and keeps the action's payload
	 * under it while the dialog is shown or the registry is current; the payload itself never leaves the worker.
	 */
	@Nullable Long handle;
	/**
	 * Dialog a {@link DialogActionType#SHOW_DIALOG} action opens, or {@code null} when the action names a registry
	 * dialog Anvil does not know or is of another type.
	 */
	@Nullable Dialog dialog;

	/**
	 * Creates an action that carries neither a dialog nor a handle.
	 *
	 * @param type action type
	 * @param value the command, template, URL, text or page the action carries
	 * @return the action
	 */
	public static @NotNull DialogAction of(@NotNull DialogActionType type, @NotNull String value) {
		return new DialogAction(type, value, null, null);
	}

	/**
	 * Creates a custom action, which sends a custom click when pressed.
	 *
	 * @param type {@link DialogActionType#CUSTOM} or {@link DialogActionType#DYNAMIC_CUSTOM}
	 * @param id the custom click's ID, such as {@code example:submit}
	 * @param handle the handle the player's worker gave the action
	 * @return the action
	 * @throws IllegalArgumentException when the type is not a custom one
	 */
	public static @NotNull DialogAction custom(@NotNull DialogActionType type, @NotNull String id, long handle) {
		if (type != DialogActionType.CUSTOM && type != DialogActionType.DYNAMIC_CUSTOM)
			throw new IllegalArgumentException("A custom action is of type CUSTOM or DYNAMIC_CUSTOM, not " + type);

		return new DialogAction(type, id, handle, null);
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
