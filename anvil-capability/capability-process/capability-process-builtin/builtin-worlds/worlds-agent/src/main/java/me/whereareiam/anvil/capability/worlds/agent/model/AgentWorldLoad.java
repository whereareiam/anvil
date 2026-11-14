package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * A request to load a world from the folder the server keeps for it, as the agent channel carries it.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldLoad {
	/**
	 * The key of the world's level, with its namespace.
	 */
	@NotNull String key;
	/**
	 * The name of the world's kind: {@code OVERWORLD}, {@code NETHER} or {@code END}.
	 */
	@NotNull String kind;
}
