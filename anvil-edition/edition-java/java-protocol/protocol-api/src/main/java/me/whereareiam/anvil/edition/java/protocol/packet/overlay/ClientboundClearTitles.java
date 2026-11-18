package me.whereareiam.anvil.edition.java.protocol.packet.overlay;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;

/**
 * Clears the title and the subtitle the player sees.
 */
@Value
@FieldNameConstants
public class ClientboundClearTitles implements ClientboundPacket {
	/**
	 * Whether the title times return to their defaults as well.
	 */
	boolean resetTimes;
}
