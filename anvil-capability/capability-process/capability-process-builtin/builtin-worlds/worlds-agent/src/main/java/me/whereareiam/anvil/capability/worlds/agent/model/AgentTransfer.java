package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A request to move one of a server's players to a location.
 */
@Value
@Builder
@Jacksonized
public class AgentTransfer {
	/**
	 * The UUID the server reports for the player.
	 */
	@NotNull UUID uniqueId;
	/**
	 * Where to move the player.
	 */
	@NotNull AgentLocation location;
}
