package me.whereareiam.anvil.agent.api.type;

/**
 * Kind of Minecraft process hosting a platform agent.
 */
public enum AgentRole {
	/**
	 * A Minecraft server process.
	 */
	SERVER,
	/**
	 * A proxy process such as Velocity or BungeeCord.
	 */
	PROXY
}
