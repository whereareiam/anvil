package me.whereareiam.anvil.capability.inventory.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One item in a slot of a client-side inventory snapshot: where it lies, what it is, how many there are and what the
 * player reads on it.
 */
@Value
@Builder(toBuilder = true)
public class InventoryItem {
	/**
	 * The container slot the item is in.
	 */
	int slot;
	/**
	 * What kind of item it is.
	 */
	@NotNull ItemType type;
	/**
	 * Number of items in the stack.
	 */
	int amount;
	/**
	 * The text the player reads on the item.
	 */
	@NotNull
	@Builder.Default
	ItemDisplay display = ItemDisplay.NONE;
}
