package me.whereareiam.anvil.edition.java.protocol.packet.overlay;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A text the server shows above the player's hotbar. Action-bar text the server sends as an overlay system chat
 * message arrives as that message instead.
 */
@Value
@FieldNameConstants
public class ClientboundSetActionBarText implements ClientboundPacket {
	/**
	 * The text as plain text.
	 */
	@NotNull String text;
}
