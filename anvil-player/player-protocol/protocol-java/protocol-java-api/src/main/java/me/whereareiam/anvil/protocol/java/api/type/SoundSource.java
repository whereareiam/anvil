package me.whereareiam.anvil.protocol.java.api.type;

/**
 * The volume sliders the game plays a sound through, named as the game's sound sources.
 */
public enum SoundSource {
	/**
	 * The master volume only.
	 */
	MASTER,
	/**
	 * Background music.
	 */
	MUSIC,
	/**
	 * Jukeboxes and note blocks.
	 */
	RECORDS,
	/**
	 * Rain and thunder.
	 */
	WEATHER,
	/**
	 * Blocks.
	 */
	BLOCKS,
	/**
	 * Hostile creatures.
	 */
	HOSTILE,
	/**
	 * Friendly creatures.
	 */
	NEUTRAL,
	/**
	 * Players.
	 */
	PLAYERS,
	/**
	 * Ambient and environment sounds.
	 */
	AMBIENT,
	/**
	 * Voice and speech.
	 */
	VOICE,
	/**
	 * User interface sounds, a source of Minecraft 1.21.6 and later.
	 */
	UI
}
