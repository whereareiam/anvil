package me.whereareiam.anvil.protocol.java.data.model;

import lombok.Value;

/**
 * How a block slows an entity inside it, as vanilla's {@code Entity.makeStuckInBlock} does for cobwebs, sweet berry
 * bushes and powder snow: the entity's movement in the tick is multiplied by these factors and its fall distance
 * reset.
 *
 * <p>The factors are vanilla's values widened to {@code double} as vanilla widens them, so {@code 0.05F} is
 * {@code 0.05000000074505806}.</p>
 */
@Value
public class StuckSpeed {
	/**
	 * The factor of the east-west and north-south movement.
	 */
	double horizontal;
	/**
	 * The factor of the vertical movement.
	 */
	double vertical;
}
