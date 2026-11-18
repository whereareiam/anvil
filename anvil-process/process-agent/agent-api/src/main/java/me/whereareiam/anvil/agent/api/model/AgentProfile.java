package me.whereareiam.anvil.agent.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player as one platform reports it: the name and UUID the platform knows the player by, which a proxy or login
 * plugin may have changed from what the client asked for.
 */
@Value
@Builder
@Jacksonized
public class AgentProfile {
	/**
	 * Player name the platform reports.
	 */
	@NotNull String username;

	/**
	 * Player UUID the platform reports.
	 */
	@NotNull UUID uniqueId;
}
