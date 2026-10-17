package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server lets the player join the game, after login or configuration, and the client replaces its screens with
 * the level. The protocol models only the player's entity ID of the packet's fields.
 */
@Value
public class ClientboundLogin implements ClientboundPacket {
	/**
	 * The protocol ID of the player's own entity, which entities riding the player name as their vehicle.
	 */
	int entityId;
}
