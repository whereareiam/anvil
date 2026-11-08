package me.whereareiam.anvil.protocol.java.api.packet.chat;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A message the server shows the player on its own behalf, in the chat or, as an overlay, above the hotbar.
 */
@Value
public class ClientboundSystemChat implements ClientboundPacket {
	/**
	 * The message as the game shows it.
	 */
	@NotNull Text text;
	/**
	 * Whether the message is shown above the hotbar instead of in the chat.
	 */
	boolean overlay;
}
