package me.whereareiam.anvil.capability.world.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * What the client was told about the server's worlds when it joined the server, which stays the same while the
 * server respawns the player in its levels.
 */
@Value
@Builder
public class ServerInfo {
	/**
	 * Whether the server is in hardcore mode, where a player who dies cannot respawn.
	 */
	boolean hardcore;
	/**
	 * The keys of every level the server has, with their namespaces, in the server's order.
	 */
	@NotNull @Singular List<String> levels;
}
