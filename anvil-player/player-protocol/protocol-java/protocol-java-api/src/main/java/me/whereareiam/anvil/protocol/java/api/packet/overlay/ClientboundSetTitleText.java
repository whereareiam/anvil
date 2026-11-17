package me.whereareiam.anvil.protocol.java.api.packet.overlay;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A title the server shows in the middle of the player's view, with the current subtitle and title times.
 */
@Value
@FieldNameConstants
public class ClientboundSetTitleText implements ClientboundPacket {
	/**
	 * The title as plain text.
	 */
	@NotNull String text;
}
