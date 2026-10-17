package me.whereareiam.anvil.capability.entities.model;

import lombok.Value;

/**
 * The item an entity shows, such as a dropped item, the item in an item frame or an item display, a thrown item
 * or a firework. Item protocol IDs depend on the release, as in the player's inventory.
 */
@Value
public class EntityItem {
	/**
	 * Protocol ID of the item in the player's release.
	 */
	int protocolId;
	/**
	 * Number of items in the stack.
	 */
	int amount;
}
