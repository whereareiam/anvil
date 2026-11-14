package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server moves the center of the world border of the player's level.
 */
@Value
public class ClientboundSetBorderCenter implements ClientboundPacket {
	/**
	 * The east-west coordinate of the new center.
	 */
	double centerX;
	/**
	 * The north-south coordinate of the new center.
	 */
	double centerZ;
}
