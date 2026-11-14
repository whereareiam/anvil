package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server changes how early the client warns of a moving world border.
 */
@Value
public class ClientboundSetBorderWarningDelay implements ClientboundPacket {
	/**
	 * How many seconds before a moving border reaches the player the client starts tinting the screen.
	 */
	int warningDelay;
}
