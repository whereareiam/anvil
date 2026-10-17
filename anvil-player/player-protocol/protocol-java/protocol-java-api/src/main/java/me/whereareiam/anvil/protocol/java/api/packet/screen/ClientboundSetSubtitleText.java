package me.whereareiam.anvil.protocol.java.api.packet.screen;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A subtitle the server shows under the current title, or under the next one when no title is shown.
 */
@Value
public class ClientboundSetSubtitleText implements ClientboundPacket {
	/**
	 * The subtitle as plain text.
	 */
	@NotNull String text;
}
