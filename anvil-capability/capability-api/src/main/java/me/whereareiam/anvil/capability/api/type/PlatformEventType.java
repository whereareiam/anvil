package me.whereareiam.anvil.capability.api.type;

/**
 * What happened to a player's connection on a process.
 */
public enum PlatformEventType {
	/**
	 * The player joined: a server, or on a proxy, a backend the proxy connected it to.
	 */
	JOINED,
	/**
	 * The player left: a server, or on a proxy, a backend it moved away from or disconnected from.
	 */
	LEFT
}
