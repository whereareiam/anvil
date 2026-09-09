package me.whereareiam.anvil.protocol.api.player;

import me.whereareiam.anvil.api.player.PlayerObservation;
import org.jetbrains.annotations.NotNull;

/**
 * Creates one player's observation view using services bound to the current scenario by assembly.
 */

public interface PlayerObservationFactory {
	/**
	 * Opens observations for a newly created native player.
	 * @param player backend-owned player
	 * @return player-scoped identity and route observations
	 */
	@NotNull PlayerObservation create(@NotNull ProtocolPlayer player);
}
