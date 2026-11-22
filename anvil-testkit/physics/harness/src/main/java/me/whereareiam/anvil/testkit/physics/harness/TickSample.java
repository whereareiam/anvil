package me.whereareiam.anvil.testkit.physics.harness;

import lombok.Value;

/**
 * What one tick left the player with: the position of its feet, its delta movement and its two collision flags.
 */
@Value
public class TickSample {
	double x;
	double y;
	double z;
	double dx;
	double dy;
	double dz;
	boolean onGround;
	boolean horizontalCollision;
}
