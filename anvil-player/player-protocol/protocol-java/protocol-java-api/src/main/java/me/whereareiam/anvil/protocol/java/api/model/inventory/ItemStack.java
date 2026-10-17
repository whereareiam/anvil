package me.whereareiam.anvil.protocol.java.api.model.inventory;

import lombok.Value;

/**
 * A stack of items in a slot, as inventory packets carry it. An empty slot carries no stack at all.
 */
@Value
public class ItemStack {
	/**
	 * Numeric ID of the item in the release's item registry.
	 */
	int id;
	/**
	 * Number of items in the stack.
	 */
	int count;
}
