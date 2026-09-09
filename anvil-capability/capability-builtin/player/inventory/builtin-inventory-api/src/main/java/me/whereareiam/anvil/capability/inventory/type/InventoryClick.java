package me.whereareiam.anvil.capability.inventory.type;

/**
 * Common inventory click modes supported by the bundled inventory capability.
 */
public enum InventoryClick {
	/**
	 * Pick up or place a full stack with the primary button.
	 */
	LEFT,
	/**
	 * Pick up or place half or one item with the secondary button.
	 */
	RIGHT,
	/**
	 * Shift-click the slot between container areas.
	 */
	SHIFT_LEFT,
	/**
	 * Swap the slot with a hotbar slot supplied as the button.
	 */
	HOTBAR_SWAP,
	/**
	 * Drop one item from the slot.
	 */
	DROP_ONE,
	/**
	 * Drop the entire slot stack.
	 */
	DROP_STACK
}
