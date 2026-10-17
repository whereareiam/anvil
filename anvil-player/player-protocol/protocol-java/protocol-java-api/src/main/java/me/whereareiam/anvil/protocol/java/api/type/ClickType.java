package me.whereareiam.anvil.protocol.java.api.type;

/**
 * The ways a player clicks a container slot, named as the game's click types. The click's button says which variant
 * of the type the player used.
 */
public enum ClickType {
	/**
	 * Picks a stack up or puts it down: button {@code 0} the whole stack, button {@code 1} half of it or one item.
	 */
	PICKUP,
	/**
	 * Moves the slot's stack to the other part of the container, as a shift-click does, with button {@code 0} or
	 * {@code 1}.
	 */
	QUICK_MOVE,
	/**
	 * Swaps the slot with a hotbar slot, the button {@code 0..8}, or with the off hand, button {@code 40}.
	 */
	SWAP,
	/**
	 * Takes a full stack of the slot's item in creative mode, with button {@code 2}.
	 */
	CLONE,
	/**
	 * Drops items from the slot: button {@code 0} one item, button {@code 1} the whole stack.
	 */
	THROW,
	/**
	 * One step of spreading the carried stack over several slots by dragging: buttons {@code 0..2} drag with the
	 * left, {@code 4..6} with the right and {@code 8..10} with the middle mouse button, each starting, adding a slot
	 * and ending the drag.
	 */
	QUICK_CRAFT,
	/**
	 * Collects every item of the carried kind into the carried stack, as a double click does, with button {@code 0}.
	 */
	PICKUP_ALL
}
