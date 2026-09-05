package me.whereareiam.anvil.api.type;

/**
 * Authentication used by a simulated player and the scenario entrypoint.
 */
public enum AuthenticationMode {
	/**
	 * Connect without contacting Mojang services; the entry point must use offline mode.
	 */
	OFFLINE,
	/**
	 * Authenticate with a named device-code profile stored outside the project.
	 */
	ONLINE
}
