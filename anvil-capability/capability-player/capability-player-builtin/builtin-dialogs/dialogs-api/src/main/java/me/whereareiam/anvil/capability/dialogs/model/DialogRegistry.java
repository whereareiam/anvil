package me.whereareiam.anvil.capability.dialogs.model;

import lombok.Value;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The dialog registry a server sent, in registry order, through which references to registry dialogs are resolved.
 */
@Value
public class DialogRegistry {
	@NotNull List<RegistryDialog> dialogs;

	/**
	 * Resolves the dialog a show-dialog packet refers to by registry ID, keeping the reference on it.
	 *
	 * @param dialog a dialog that carries only its registry ID
	 * @return the registry's dialog, or the reference with its registry name when the registry has no data for it
	 */
	public @NotNull Dialog resolve(@NotNull Dialog dialog) {
		Integer id = dialog.getRegistryId();
		if (id == null || id < 0 || id >= dialogs.size()) return dialog;

		RegistryDialog entry = dialogs.get(id);
		Dialog known = entry.getDialog();
		if (known == null) return dialog.toBuilder().registryName(entry.getName()).build();
		return known.toBuilder().registryId(id).registryName(entry.getName()).build();
	}

	/**
	 * Finds a registry dialog by its name.
	 *
	 * @param name registry name, such as {@code example:rules}
	 * @return the dialog, or {@code null} when the registry has no data for it
	 */
	public @Nullable Dialog find(@NotNull String name) {
		for (int id = 0; id < dialogs.size(); id++) {
			RegistryDialog entry = dialogs.get(id);
			if (entry.getName().equals(name) && entry.getDialog() != null)
				return entry.getDialog().toBuilder().registryId(id).registryName(name).build();
		}
		return null;
	}
}
