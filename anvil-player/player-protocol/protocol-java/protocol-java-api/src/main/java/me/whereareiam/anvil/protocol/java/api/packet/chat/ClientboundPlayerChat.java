package me.whereareiam.anvil.protocol.java.api.packet.chat;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A chat message a player sent, as the server relays it.
 */
@Value
public class ClientboundPlayerChat implements ClientboundPacket {
	/**
	 * The message as plain text: the server's unsigned replacement when it sent one, otherwise the signed content.
	 */
	@NotNull String text;
}
