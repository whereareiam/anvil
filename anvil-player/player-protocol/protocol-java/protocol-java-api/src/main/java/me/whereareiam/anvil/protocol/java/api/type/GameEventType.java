package me.whereareiam.anvil.protocol.java.api.type;

/**
 * The state changes of the game's game event packet, named as the game's game event types.
 */
public enum GameEventType {
	/**
	 * The player's respawn block was missing or obstructed when it respawned.
	 */
	NO_RESPAWN_BLOCK_AVAILABLE,
	/**
	 * Rain starts.
	 */
	START_RAINING,
	/**
	 * Rain stops.
	 */
	STOP_RAINING,
	/**
	 * The player's game mode changes.
	 */
	CHANGE_GAME_MODE,
	/**
	 * The player leaves the End through its exit portal and sees the credits.
	 */
	WIN_GAME,
	/**
	 * A demo message.
	 */
	DEMO_EVENT,
	/**
	 * An arrow the player shot hit another player.
	 */
	ARROW_HIT_PLAYER,
	/**
	 * The rain level changes.
	 */
	RAIN_LEVEL_CHANGE,
	/**
	 * The thunder level changes.
	 */
	THUNDER_LEVEL_CHANGE,
	/**
	 * A pufferfish stung the player.
	 */
	PUFFER_FISH_STING,
	/**
	 * An elder guardian's curse appears.
	 */
	GUARDIAN_ELDER_EFFECT,
	/**
	 * Whether the player respawns at once instead of seeing the death screen.
	 */
	IMMEDIATE_RESPAWN,
	/**
	 * Whether the player may craft only the recipes it unlocked, an event of Minecraft 1.20.3 and later.
	 */
	LIMITED_CRAFTING,
	/**
	 * The client may start waiting for the level's chunks, an event of Minecraft 1.20.3 and later.
	 */
	LEVEL_CHUNKS_LOAD_START
}
