package me.whereareiam.anvil.protocol.java.api.packet.dialog;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A custom click the player sends, such as a dialog button's custom action, for the server to handle by its ID.
 * Releases before Minecraft 1.21.6 have no custom clicks.
 */
@Value
public class ServerboundCustomClickAction implements ServerboundPacket {
	/**
	 * The click's ID with its namespace, such as {@code example:submit}.
	 */
	@NotNull String id;
	/**
	 * The payload the server receives, or {@code null} to send none. For a dynamic custom click the client sends the
	 * action's additions with the value of each input put in under the input's key.
	 */
	@Nullable NbtCompound payload;
}
