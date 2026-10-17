package me.whereareiam.anvil.platform.api.type;

/**
 * Player-identity forwarding protocols that platform providers can negotiate.
 */
public enum ForwardingMode {
	/**
	 * No proxy identity forwarding, including direct server connections.
	 */
	NONE,
	/**
	 * Bungee-compatible legacy handshake forwarding.
	 */
	LEGACY,
	/**
	 * Velocity-compatible authenticated modern forwarding.
	 */
	MODERN
}
