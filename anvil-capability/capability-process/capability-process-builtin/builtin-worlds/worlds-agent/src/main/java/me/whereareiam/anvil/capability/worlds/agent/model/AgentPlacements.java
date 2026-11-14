package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Where a server says each of its players is.
 */
@Value
@Builder
@Jacksonized
public class AgentPlacements {
	/**
	 * One placement for each player on the server.
	 */
	@NotNull @Singular List<AgentPlacement> placements;
}
