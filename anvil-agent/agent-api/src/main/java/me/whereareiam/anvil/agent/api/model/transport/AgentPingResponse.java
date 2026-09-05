package me.whereareiam.anvil.agent.api.model.transport;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Wire response used to verify an agent and identify its platform.
 */
@Value
@Builder
@Jacksonized
public class AgentPingResponse {
	/**
	 * Stable Anvil platform identifier.
	 */
	@NotNull String platform;
	/**
	 * Platform-reported version.
	 */
	@NotNull String version;
	/**
	 * Lower-case platform role identifier.
	 */
	@NotNull String role;
}
