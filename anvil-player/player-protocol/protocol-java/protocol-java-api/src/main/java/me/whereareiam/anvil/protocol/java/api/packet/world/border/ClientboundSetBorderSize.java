package me.whereareiam.anvil.protocol.java.api.packet.world.border;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server sets the world border of the player's level to a size at once.
 */
@Value
@FieldNameConstants
public class ClientboundSetBorderSize implements ClientboundPacket {
	/**
	 * The border's new side length in blocks.
	 */
	double size;
}
