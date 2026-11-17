package me.whereareiam.anvil.protocol.java.api.type.player;

/**
 * The game mode the server puts the player in, which decides whether it may fly, can be hurt and collides with blocks.
 */
public enum GameMode {
	/**
	 * The player is hurt, collides with blocks and flies only when the server allows it.
	 */
	SURVIVAL,
	/**
	 * The player may fly, cannot be hurt and builds without items.
	 */
	CREATIVE,
	/**
	 * The player plays as in survival but cannot break or place blocks freely.
	 */
	ADVENTURE,
	/**
	 * The player always flies and passes through blocks.
	 */
	SPECTATOR
}
