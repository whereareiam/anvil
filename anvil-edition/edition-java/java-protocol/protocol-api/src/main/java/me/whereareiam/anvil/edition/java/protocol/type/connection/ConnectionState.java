package me.whereareiam.anvil.edition.java.protocol.type.connection;

/**
 * The state of a player's connection, which decides what each packet ID means: the same ID names different packets
 * while the player logs in, while the server configures it and while it plays.
 */
public enum ConnectionState {
	/**
	 * The player is logging in: encryption, compression and the granted profile.
	 */
	LOGIN,
	/**
	 * The server configures the player before it joins a level or between levels: registries, tags and resource
	 * packs. Releases before Minecraft 1.20.2 have no such state.
	 */
	CONFIGURATION,
	/**
	 * The player plays in a level.
	 */
	PLAY
}
