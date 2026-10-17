package me.whereareiam.anvil.protocol.java.api.model.screen;

import lombok.Value;

/**
 * Changes a boss bar's flags.
 */
@Value
public class BossEventUpdateProperties implements BossEventOperation {
	/**
	 * Whether the bar darkens the sky.
	 */
	boolean darkenScreen;
	/**
	 * Whether the bar plays the dragon fight music.
	 */
	boolean playMusic;
	/**
	 * Whether the bar creates fog.
	 */
	boolean createWorldFog;
}
