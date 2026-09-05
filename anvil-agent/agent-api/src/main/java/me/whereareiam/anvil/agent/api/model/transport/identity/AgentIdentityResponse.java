package me.whereareiam.anvil.agent.api.model.transport.identity;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Wire response containing a player identity observed by a platform agent.
 */
@Value
@Builder
@Jacksonized
public class AgentIdentityResponse {
	/**
	 * Player name reported by the platform.
	 */
	@NotNull String username;
	/**
	 * Player unique identifier in its external string representation.
	 */
	@JsonAlias("uuid")
	@NotNull String uniqueId;
	/**
	 * Platform location where the player was observed.
	 */
	@NotNull AgentLocationPayload location;
}
