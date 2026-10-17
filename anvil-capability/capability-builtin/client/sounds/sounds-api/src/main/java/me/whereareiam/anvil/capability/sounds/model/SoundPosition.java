package me.whereareiam.anvil.capability.sounds.model;

import lombok.Value;

/**
 * The world coordinates a sound plays at.
 */
@Value
public class SoundPosition {
	/**
	 * The x coordinate.
	 */
	double x;
	/**
	 * The y coordinate.
	 */
	double y;
	/**
	 * The z coordinate.
	 */
	double z;
}
