package me.whereareiam.anvil.capability.sounds.type;

/**
 * The volume sliders a sound belongs to, named as Minecraft's sound sources.
 */
public enum SoundCategory {
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
	 * User interface sounds, a slider of newer Minecraft releases.
	 */
	UI
}
