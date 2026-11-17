package me.whereareiam.anvil.protocol.java.api.packet.screen;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * Clears the title and the subtitle the player's screen shows.
 */
@Value
@FieldNameConstants
public class ClientboundClearTitles implements ClientboundPacket {
	/**
	 * Whether the title times return to their defaults as well.
	 */
	boolean resetTimes;
}
