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
	 * Authenticate with a named account stored outside the project; the entry point must use online mode.
	 */
	ONLINE,
	/**
	 * Sign in with a named account stored outside the project and authenticate when the entry point asks for it.
	 * The entry point may use offline mode, where a plugin decides for each connection whether it requires
	 * authentication; a connection it does not challenge joins unauthenticated.
	 */
	ON_REQUEST;

	/**
	 * Returns whether a player in this mode signs in with a stored account.
	 *
	 * @return true for every mode except {@link #OFFLINE}
	 */
	public boolean usesAccount() {
		return this != OFFLINE;
	}
}
