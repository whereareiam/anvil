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
	 * The dialog, or {@code null} when the server left it out for the client's own data pack and this Anvil version
	 * does not know that dialog. Anvil knows the built-in dialogs of every release from Minecraft 1.21.6 until 26.3.
	 */
	@Nullable Dialog dialog;
}
