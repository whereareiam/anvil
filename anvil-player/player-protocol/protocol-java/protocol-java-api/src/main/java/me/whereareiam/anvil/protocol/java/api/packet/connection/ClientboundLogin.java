package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server lets the player join the game, after login or configuration, and the client replaces its screens and its
 * chunks with the new level. The protocol models the player's entity ID and the level of the packet's fields.
 */
@Value
public class ClientboundLogin implements ClientboundPacket {
	/**
	 * The protocol ID of the player's own entity, which entities riding the player name as their vehicle.
	 */
	int entityId;
	/**
	 * The level the player joins.
	 */
	@NotNull Dimension dimension;
}
