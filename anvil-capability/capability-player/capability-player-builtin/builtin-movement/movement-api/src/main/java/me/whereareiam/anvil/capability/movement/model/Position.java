package me.whereareiam.anvil.capability.movement.model;

import lombok.Builder;
import lombok.Value;

/**
 * Where a player is and which way it looks: the position of its feet and its view direction, with whether it stands
 * on the ground.
 *
 * <pre>{@code
 * Position spawn = Position.builder().x(0.5).y(64).z(0.5).yaw(90).onGround(true).build();
 * }</pre>
 */
@Value
@Builder
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
	 * The vertical view direction in degrees, from -90 looking up to 90 looking down.
	 */
	float pitch;
	/**
	 * Whether the player stands on the ground.
	 */
	boolean onGround;
}
