package me.whereareiam.anvil.capability.protocol.api.model;

import lombok.Value;

/**
 * Immutable Minecraft view direction in degrees, shared by native operations and server corrections.
 */
@Value
public class ViewRotation {
	/**
	 * Horizontal rotation in degrees.
	 */
	float yaw;
	/**
	 * Vertical rotation in degrees.
	 */
	float pitch;
}
