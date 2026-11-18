package me.whereareiam.anvil.edition.java.protocol.packet.world.border;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;

/**
 * The server changes how early the client warns of a moving world border.
 */
@Value
@FieldNameConstants
public class ClientboundSetBorderWarningDelay implements ClientboundPacket {
	/**
	 * How many seconds before a moving border reaches the player the client starts tinting the screen.
	 */
	int warningDelay;
}
