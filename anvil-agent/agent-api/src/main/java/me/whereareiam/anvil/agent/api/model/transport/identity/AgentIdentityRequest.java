package me.whereareiam.anvil.agent.api.model.transport.identity;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Wire request for looking up a player identity.
 */
@Value
@Builder
@Jacksonized
public class AgentIdentityRequest {
	/**
	 * Player name to look up.
	 */
	@NotNull String username;
}
