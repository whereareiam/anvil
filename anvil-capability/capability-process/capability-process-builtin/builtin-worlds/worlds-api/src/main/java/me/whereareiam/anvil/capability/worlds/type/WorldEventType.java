package me.whereareiam.anvil.capability.worlds.type;

/**
 * What happened to one of a server's worlds.
 */
public enum WorldEventType {
	/**
	 * The server loaded the world, such as one a plugin or a test created.
	 */
	LOADED,
	/**
	 * The server unloaded the world.
	 */
	UNLOADED
}
