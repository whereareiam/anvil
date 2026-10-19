package me.whereareiam.anvil.capability.movement.model;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable player position and view direction.
 */
@Value
@Builder(toBuilder = true)
public class Position {
	/**
	 * The east-west coordinate of the player's feet.
	 */
	double x;
	/**
	 * The height of the player's feet.
	 */
	double y;
	/**
	 * The north-south coordinate of the player's feet.
	 */
	double z;
	/**
	 * The horizontal view direction in degrees.
	 */
	float yaw;
	/**
	 * The vertical view direction in degrees.
	 */
	float pitch;
	/**
	 * Whether the player stands on the ground.
	 */
	boolean onGround;
}
