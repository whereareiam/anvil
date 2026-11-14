package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The worlds a server has loaded, in the server's order.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldList {
	/**
	 * The worlds, starting with the server's main world.
	 */
	@NotNull @Singular List<AgentWorld> worlds;
}
