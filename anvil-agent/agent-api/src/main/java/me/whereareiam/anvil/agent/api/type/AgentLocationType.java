package me.whereareiam.anvil.agent.api.type;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Closed set of locations reported by a platform agent.
 */
public enum AgentLocationType {
	/**
	 * A Minecraft server location.
	 */
	@JsonProperty("server")
	SERVER,
	/**
	 * A proxy location.
	 */
	@JsonProperty("proxy")
	PROXY
}
