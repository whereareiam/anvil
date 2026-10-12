package me.whereareiam.anvil.capability.dialogs.model.dialog;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of the dialog registry the server sent during configuration.
 */
@Value
public class RegistryDialog {
	/**
	 * Registry name, such as {@code minecraft:server_links}.
	 */
	@NotNull String name;
	/**
	 * The dialog, or {@code null} when the server left it out because the client knows it from a built-in pack.
	 */
	@Nullable Dialog dialog;
}
