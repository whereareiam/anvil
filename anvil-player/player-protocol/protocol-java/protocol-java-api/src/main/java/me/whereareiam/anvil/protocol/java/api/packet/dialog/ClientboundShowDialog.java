package me.whereareiam.anvil.protocol.java.api.packet.dialog;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.Nullable;

/**
 * A dialog the server shows the player, in the game or during configuration. In the game the server may refer to a
 * dialog of its dialog registry by ID instead of sending it; during configuration it always sends the dialog itself,
 * as the NBT the game's dialog codec writes. Releases before Minecraft 1.21.6 have no dialogs.
 */
@Value
public class ClientboundShowDialog implements ClientboundPacket {
	/**
	 * Registry ID of the dialog the server refers to, its index in the {@code minecraft:dialog} registry, or
	 * {@code null} when the packet carries the dialog itself.
	 */
	@Nullable Integer registryId;
	/**
	 * The dialog the packet carries, as the game's dialog codec writes it, or {@code null} when it refers to a
	 * registry dialog.
	 */
	@Nullable NbtCompound dialog;
	/**
	 * Whether the server sent the packet during the configuration phase.
	 */
	boolean configuration;
}
