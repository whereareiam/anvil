package me.whereareiam.anvil.protocol.java.api.model.sound;

import lombok.Value;

/**
 * The world coordinates a sound plays at, in blocks.
 */
@Value
public class SoundPosition {
	/**
	 * The east-west coordinate.
	 */
	double x;
	/**
	 * The height.
	 */
	double y;
	/**
	 * The north-south coordinate.
	 */
	double z;
}
