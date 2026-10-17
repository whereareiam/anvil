package me.whereareiam.anvil.capability.dialogs.packet;

import lombok.Value;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of the dialog registry as a release's registry packet carries it, which
 * {@link DialogsPacketListener#registry(java.util.List)} reports to the dialogs binding.
 */
@Value
public class RegistryDialogEntry {
	/**
	 * Registry name, such as {@code minecraft:server_links}.
	 */
	@NotNull String name;
	/**
	 * The dialog compound, or {@code null} when the server left it out because the client knows it from a built-in
	 * pack.
	 */
	@Nullable NbtCompound dialog;
}
