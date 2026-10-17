package me.whereareiam.anvil.protocol.java.api.packet.screen;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A text the server shows above the player's hotbar. Action-bar text the server sends as an overlay system chat
 * message arrives as that message instead.
 */
@Value
public class ClientboundSetActionBarText implements ClientboundPacket {
	/**
	 * The text as plain text.
	 */
	@NotNull String text;
}
