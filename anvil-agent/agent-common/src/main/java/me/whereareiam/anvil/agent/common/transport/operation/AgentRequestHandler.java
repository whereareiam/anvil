package me.whereareiam.anvil.agent.common.transport.operation;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import org.jetbrains.annotations.NotNull;


/**
 * Internal implementation of one platform-agent transport operation.
 */
public interface AgentRequestHandler {
	/**
	 * Returns the operation identifier used on the authenticated wire.
	 *
	 * @return wire operation identifier
	 */
	@NotNull String wireName();

	/**
	 * Handles one decoded request.
	 *
	 * @param platformAgent typed platform implementation
	 * @param arguments decoded operation arguments
	 * @return encoded operation result
	 */
	@NotNull JsonNode handle(@NotNull PlatformAgent platformAgent, @NotNull JsonNode arguments) throws Exception;
}
