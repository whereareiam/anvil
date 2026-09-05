package me.whereareiam.anvil.agent.api.model.transport.identity;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.agent.api.type.AgentLocationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wire representation of a server or proxy player location.
 */
@Value
@Builder
@Jacksonized
public class AgentLocationPayload {
	/**
	 * Location kind.
	 */
	@NotNull AgentLocationType type;
	/**
	 * Server name when the location is a server.
	 */
	@Nullable String server;
	/**
	 * Proxy name when the location is a proxy.
	 */
	@Nullable String proxy;
	/**
	 * Connected backend server when the location is a proxy.
	 */
	@Nullable String connectedServer;
}
