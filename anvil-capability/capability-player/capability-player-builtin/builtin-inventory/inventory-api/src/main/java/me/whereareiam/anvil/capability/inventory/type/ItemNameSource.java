package me.whereareiam.anvil.capability.inventory.type;

/**
 * Where the name a player reads on an item comes from.
 */
public enum ItemNameSource {
	/**
	 * A player or plugin renamed the stack, as an anvil does; the client shows such names in italics unless their
	 * style says otherwise.
	 */
	CUSTOM,
	/**
	 * The stack carries an item name of its own, which Minecraft 1.20.5 introduced and plugins set to name an item
	 * without it counting as renamed.
	 */
	ITEM
}
