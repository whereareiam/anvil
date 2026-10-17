package me.whereareiam.anvil.agent.api.type;

/**
 * What happened to a player's connection on a platform.
 */
public enum AgentPlayerEventType {
	/**
	 * The player joined: a server, or on a proxy, a backend the proxy connected it to.
	 */
	JOINED,
	/**
	 * The player left: a server, or on a proxy, a backend it moved away from or disconnected from.
	 */
	LEFT
}
