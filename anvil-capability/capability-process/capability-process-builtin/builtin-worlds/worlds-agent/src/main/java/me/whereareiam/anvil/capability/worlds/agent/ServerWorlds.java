package me.whereareiam.anvil.capability.worlds.agent;

import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldCreation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldLoad;
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

	/**
	 * Creates a world and loads it.
	 *
	 * @param creation the world's key, kind, seed and generator
	 * @return the world the server created
	 * @throws IllegalArgumentException when the server has already loaded a world of that key
	 * @throws UnsupportedOperationException when the platform cannot create such a world, with the reason
	 * @throws IllegalStateException when the server refused to create it, with the reason
	 */
	@NotNull AgentWorld create(@NotNull AgentWorldCreation creation);

	/**
	 * Loads a world from the folder the server keeps for it.
	 *
	 * @param load the world's key and kind
	 * @return the world the server loaded
	 * @throws IllegalArgumentException when the server has already loaded the world or has no folder for it
	 * @throws UnsupportedOperationException when the platform cannot load such a world, with the reason
	 * @throws IllegalStateException when the server refused to load it, with the reason
	 */
	@NotNull AgentWorld load(@NotNull AgentWorldLoad load);

	/**
	 * Saves a world and unloads it.
	 *
	 * @param key the key of the world's level
	 * @throws IllegalArgumentException when the server has not loaded the world
	 * @throws UnsupportedOperationException when the platform cannot unload a world, with the reason
	 * @throws IllegalStateException when the server refused to unload it, with the reason
	 */
	void unload(@NotNull String key);

	/**
	 * Unloads a world without saving it and deletes its folder.
	 *
	 * @param key the key of the world's level
	 * @throws IllegalArgumentException when the server has not loaded the world
	 * @throws UnsupportedOperationException when the platform cannot unload a world, with the reason
	 * @throws IllegalStateException when the server refused to unload it or its folder could not be deleted, with the
	 *         reason
	 */
	void delete(@NotNull String key);
}
