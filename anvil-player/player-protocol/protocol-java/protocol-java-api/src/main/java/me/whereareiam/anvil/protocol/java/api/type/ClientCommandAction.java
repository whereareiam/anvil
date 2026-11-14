package me.whereareiam.anvil.protocol.java.api.type;

/**
 * What a client command asks the server for, named as the game's client command actions.
 */
public enum ClientCommandAction {
	/**
	 * Respawn the dead player.
	 */
	PERFORM_RESPAWN,
	/**
	 * Send the player's statistics.
	 */
	REQUEST_STATS
}
