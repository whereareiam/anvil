package me.whereareiam.anvil.protocol.java.api.packet.screen;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A title the server shows in the middle of the player's screen, with the current subtitle and title times.
 */
@Value
public class ClientboundSetTitleText implements ClientboundPacket {
	/**
	 * The title as plain text.
	 */
	@NotNull String text;
}
