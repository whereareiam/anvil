package me.whereareiam.anvil.protocol.java.api.model.entity;

import lombok.Value;

/**
 * The position of an entity in the player's world, in blocks.
 */
@Value
public class EntityPosition {
	/**
	 * The east-west coordinate.
	 */
	double x;
	/**
	 * The height of the entity's feet.
	 */
	double y;
	/**
	 * The north-south coordinate.
	 */
	double z;
}
