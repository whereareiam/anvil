package me.whereareiam.anvil.protocol.java.api.model.interaction;

import lombok.Builder;
import lombok.Value;

/**
 * The point of an entity a player interacts with, in blocks relative to the entity's position.
 */
@Value
@Builder
public class InteractionPoint {
	/**
	 * The east-west offset.
	 */
	double x;
	/**
	 * The vertical offset.
	 */
	double y;
	/**
	 * The north-south offset.
	 */
	double z;
}
