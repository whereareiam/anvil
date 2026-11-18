package me.whereareiam.anvil.edition.java.protocol.packet.chat;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * A message the server shows the player on its own behalf, in the chat or, as an overlay, above the hotbar.
 */
@Value
@FieldNameConstants
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
