package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Where a server says one of its players is.
 */
@Value
@Builder
@Jacksonized
public class AgentPlacement {
	/**
	 * The UUID the server reports for the player.
	 */
	@NotNull UUID uniqueId;
	/**
	 * The player's world, position and view.
	 */
	@NotNull AgentLocation location;
}
