package me.whereareiam.anvil.edition.java.protocol.packet.chat;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.chat.PackedMessageSignature;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server deletes a player's chat message from the client's chat, naming it by its signature. Releases before
 * Minecraft 1.19.1 cannot delete chat.
 */
@Value
@FieldNameConstants
public class ClientboundDeleteChat implements ClientboundPacket {
	/**
	 * The signature of the message to delete, packed against the client's signature cache.
	 */
	@NotNull PackedMessageSignature signature;
}
