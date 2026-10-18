package me.whereareiam.anvil.protocol.java.api.packet.chat;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.chat.PackedMessageSignature;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server deletes a player's chat message from the client's chat, naming it by its signature. Releases before
 * Minecraft 1.19.1 cannot delete chat.
 */
@Value
public class ClientboundDeleteChat implements ClientboundPacket {
	/**
	 * The signature of the message to delete, packed against the client's signature cache.
	 */
	@NotNull PackedMessageSignature signature;
}
