package me.whereareiam.anvil.api.type.network;

/**
 * The connection phase a mod's payload channel carries payloads in.
 */
public enum ChannelPhase {
	/**
	 * The configuration phase, between login and play, in which the server sends registries and settings.
	 */
	CONFIGURATION,
	/**
	 * The play phase, once the player is in the world.
	 */
	PLAY
}
