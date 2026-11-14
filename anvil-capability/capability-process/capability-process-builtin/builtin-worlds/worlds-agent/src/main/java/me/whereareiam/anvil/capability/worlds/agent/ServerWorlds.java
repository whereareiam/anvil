package me.whereareiam.anvil.capability.worlds.agent;

import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * One platform's worlds, which its platform side implements over the platform's own API. The operations call it on
 * the thread the platform requires, through {@link me.whereareiam.anvil.agent.server.api.PlatformAgent#call}.
 */
public interface ServerWorlds {
	/**
	 * Returns the worlds the server has loaded, starting with its main world.
	 *
	 * @return the worlds
	 */
	@NotNull List<AgentWorld> worlds();

	/**
	 * Returns where each player on the server is.
	 *
	 * @return one placement for each player
	 */
	@NotNull List<AgentPlacement> placements();

	/**
	 * Moves a player to a location.
	 *
	 * @param uniqueId the UUID the server reports for the player
	 * @param location where to move the player
	 * @return whether the server moved the player; {@code false} when the server refused, such as when a plugin
	 *         cancelled the teleport
	 * @throws IllegalArgumentException when the player is not on the server or the server has not loaded the world
	 */
	boolean teleport(@NotNull UUID uniqueId, @NotNull AgentLocation location);
}
