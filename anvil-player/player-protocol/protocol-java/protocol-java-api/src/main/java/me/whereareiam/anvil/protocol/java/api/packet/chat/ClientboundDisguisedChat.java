package me.whereareiam.anvil.protocol.java.api.packet.chat;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A chat message the server relays without a sender's signature, such as one sent from the console.
 */
@Value
@FieldNameConstants
public class ClientboundDisguisedChat implements ClientboundPacket {
	/**
	 * The message's content as the game shows it, without the chat type's decoration around it.
	 */
	@NotNull Text text;
}
