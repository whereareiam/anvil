package me.whereareiam.anvil.edition.java.protocol.model.overlay;

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
