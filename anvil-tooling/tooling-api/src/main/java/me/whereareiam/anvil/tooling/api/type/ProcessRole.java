package me.whereareiam.anvil.tooling.api.type;

/**
 * The declared role of a process in a scenario topology, independent of its runtime state.
 */
public enum ProcessRole {
	/**
	 * A Minecraft server accepting players directly or through a proxy.
	 */
	SERVER,

	/**
	 * A proxy routing players to declared backend server names.
	 */
	PROXY
}
