package me.whereareiam.anvil.api.type;

/**
 * Why a simulated player's connection ended.
 */
public enum DisconnectCause {
	/**
	 * The server or proxy disconnected the player and sent a reason, as a kick does.
	 */
	SERVER,
	/**
	 * The entry point required online authentication from a player that signs in with no account.
	 */
	AUTHENTICATION_REQUIRED,
	/**
	 * The connection closed without a reason from the server.
	 */
	CONNECTION_LOST,
	/**
	 * The player disconnected itself, as a test's own disconnect or reconnect does.
	 */
	CLIENT
}
