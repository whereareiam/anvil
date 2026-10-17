package me.whereareiam.anvil.protocol.java.api.model.entity;

import lombok.Builder;
import lombok.Value;

/**
 * The flags every entity keeps in its first entity data value.
 */
@Value
@Builder
public class SharedFlags implements EntityDataValue {
	/**
	 * Whether the entity is on fire.
	 */
	boolean onFire;
	/**
	 * Whether the entity, usually a player, sneaks.
	 */
	boolean sneaking;
	/**
	 * Whether the entity sprints.
	 */
	boolean sprinting;
	/**
	 * Whether the entity swims.
	 */
	boolean swimming;
	/**
	 * Whether the entity is invisible.
	 */
	boolean invisible;
	/**
	 * Whether the entity glows, outlined through walls.
	 */
	boolean glowing;
	/**
	 * Whether the entity glides with elytra.
	 */
	boolean gliding;
}
