package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server lets the player join the game, after login or configuration, and the client replaces its screens with
 * the level. The protocol models the player's entity ID and the dimension the player joins of the packet's fields.
 */
@Value
public class ClientboundLogin implements ClientboundPacket {
	/**
	 * The protocol ID of the player's own entity, which entities riding the player name as their vehicle.
	 */
	int entityId;
	/**
	 * The key of the level the player joins, with its namespace, such as {@code minecraft:overworld}: the dimension
	 * as the server names it, which on a server with several worlds of one dimension type tells them apart.
	 */
	@NotNull String dimension;
}
