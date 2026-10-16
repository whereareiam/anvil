package me.whereareiam.anvil.api.type.network;

/**
 * Transport protocol a process's player-facing game listener accepts connections on.
 * The platform decides it; execution reserves, binds and publishes the game port with it, and a player
 * connects with it. Agent connections are not game listeners and always use TCP.
 */
public enum ListenerTransport {
	/**
	 * Stream connections over TCP.
	 */
	TCP,

	/**
	 * Datagrams over UDP, for a platform whose game protocol runs its own session layer on UDP.
	 */
	UDP
}
