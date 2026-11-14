package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server sets the world border of the player's level to a size at once.
 */
@Value
public class ClientboundSetBorderSize implements ClientboundPacket {
	/**
	 * The border's new side length in blocks.
	 */
	double size;
}
