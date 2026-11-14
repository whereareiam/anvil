package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A request to create a world, as the agent channel carries it.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldCreation {
	/**
	 * The key of the new world's level, with its namespace.
	 */
	@NotNull String key;
	/**
	 * The name of the world's kind: {@code OVERWORLD}, {@code NETHER} or {@code END}.
	 */
	@NotNull String kind;
	/**
	 * The seed of the world's terrain, or {@code null} for one the server picks.
	 */
	@Nullable Long seed;
	/**
	 * The name of the world's generator: {@code DEFAULT}, {@code FLAT} or {@code EMPTY}.
	 */
	@NotNull String generator;
}
