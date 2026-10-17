package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.movement.model.Position;
import org.jetbrains.annotations.NotNull;

/**
 * Controls the position and view direction sent by a simulated player.
 */
public interface Movement extends PlayerCapability {
	/**
	 * Sends an absolute position and view update.
	 *
	 * @param position new player position
	 */
	void move(@NotNull Position position);
}
