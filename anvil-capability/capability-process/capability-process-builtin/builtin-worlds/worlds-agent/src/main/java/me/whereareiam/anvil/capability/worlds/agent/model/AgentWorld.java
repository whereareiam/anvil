package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * A world a server has loaded, as the agent channel carries it.
 */
@Value
@Builder
@Jacksonized
public class AgentWorld {
	/**
	 * The key of the world's level, with its namespace.
	 */
	@NotNull String key;
	/**
	 * The name of the world's kind: {@code OVERWORLD}, {@code NETHER}, {@code END} or {@code CUSTOM}.
	 */
	@NotNull String kind;
	/**
	 * Where players arrive in the world.
	 */
	@NotNull AgentLocation spawn;
}
