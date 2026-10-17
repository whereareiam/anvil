package me.whereareiam.anvil.protocol.mcprotocol.client.type;

/**
 * A value of an entity's data that the Java Edition protocol models, which an {@code EntityDataLayout} finds at an
 * index of a release's entity data.
 */
public enum EntityDataField {
	/**
	 * The flags every entity keeps, such as whether it is on fire or invisible.
	 */
	SHARED_FLAGS,
	/**
	 * The entity's custom name, if any.
	 */
	CUSTOM_NAME,
	/**
	 * Whether the entity's custom name is shown.
	 */
	CUSTOM_NAME_VISIBLE,
	/**
	 * The item an entity shows, such as a dropped item or the item of an item frame or item display.
	 */
	SHOWN_ITEM,
	/**
	 * The text of a text display.
	 */
	DISPLAY_TEXT
}
