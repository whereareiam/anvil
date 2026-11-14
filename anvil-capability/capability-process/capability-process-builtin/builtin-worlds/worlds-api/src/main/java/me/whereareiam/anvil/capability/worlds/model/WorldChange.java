package me.whereareiam.anvil.capability.worlds.model;

import lombok.Value;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * A player moving from one of a server's worlds to another, as the server reports it: a teleport, a portal, or a
 * respawn in another world. Joining or leaving the server is no world change.
 */
@Value
public class WorldChange {
	/**
	 * The player as the server reports it.
	 */
	@NotNull PlayerProfile profile;
	/**
	 * The key of the world the player left.
	 */
	@NotNull String from;
	/**
	 * The key of the world the player is in afterwards.
	 */
	@NotNull String to;
	/**
	 * When the server reported the change.
	 */
	@NotNull Instant at;
}
