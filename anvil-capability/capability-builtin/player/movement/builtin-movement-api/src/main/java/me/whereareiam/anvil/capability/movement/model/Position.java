package me.whereareiam.anvil.capability.movement.model;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable player position and view direction.
 */
@Value
@Builder
public class Position {
	double x;
	double y;
	double z;
	float yaw;
	float pitch;
	boolean onGround;
}
