package me.whereareiam.anvil.protocol.java.api.packet.screen;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * Clears the title and the subtitle the player's screen shows.
 */
@Value
public class ClientboundClearTitles implements ClientboundPacket {
	/**
	 * Whether the title times return to their defaults as well.
	 */
	boolean resetTimes;
}
