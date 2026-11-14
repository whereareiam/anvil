package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server lets the player join the game, after login or configuration, and the client replaces its screens with
 * the level. The protocol models the player's entity ID, the server's levels, whether it is hardcore, and the level
 * the player joins of the packet's fields.
 */
@Value
@Builder
public class ClientboundLogin implements ClientboundPacket {
	/**
	 * The protocol ID of the player's own entity, which entities riding the player name as their vehicle.
	 */
	int entityId;
	/**
	 * The keys of every level the server has, with their namespaces, in the server's order.
	 */
	@NotNull @Singular List<String> levels;
	/**
	 * Whether the server is in hardcore mode, where a player who dies cannot respawn.
	 */
	boolean hardcore;
	/**
	 * The key of the level the player joins, with its namespace, such as {@code minecraft:overworld}: the dimension
	 * as the server names it, which on a server with several worlds of one dimension type tells them apart.
	 */
	@NotNull String dimension;
	/**
	 * The type of the dimension the player joins.
	 */
	@NotNull DimensionType dimensionType;
	/**
	 * Whether the level is a debug world, which shows every block state and cannot be changed.
	 */
	boolean debug;
	/**
	 * Whether the level is a superflat world, which the client renders with a lower horizon.
	 */
	boolean flat;
}
