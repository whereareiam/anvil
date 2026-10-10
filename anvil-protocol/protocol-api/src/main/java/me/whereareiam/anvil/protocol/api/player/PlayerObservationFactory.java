package me.whereareiam.anvil.protocol.api.player;

import me.whereareiam.anvil.api.player.PlayerObservation;
import org.jetbrains.annotations.NotNull;

/**
 * Creates one player's observation view using services bound to the current scenario by assembly.
 */

public interface PlayerObservationFactory {
	/**
	 * Opens observations for a newly created native player.
	 *
	 * @param player library-owned player
	 * @param connectedTo name of the server or proxy the player connects to; several players may share a
	 * username, so observations follow this player's own connection from there
	 * @return player-scoped identity and route observations
	 */
	@NotNull PlayerObservation create(@NotNull ProtocolPlayer player, @NotNull String connectedTo);
}
