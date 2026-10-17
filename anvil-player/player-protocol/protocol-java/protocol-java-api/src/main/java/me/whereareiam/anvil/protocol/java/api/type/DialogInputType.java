package me.whereareiam.anvil.protocol.java.api.type;

/**
 * The input controls of a dialog, named after the input types of the game's dialogs.
 */
public enum DialogInputType {
	/**
	 * {@code minecraft:text}: a text field.
	 */
	TEXT,
	/**
	 * {@code minecraft:boolean}: a checkbox.
	 */
	BOOLEAN,
	/**
	 * {@code minecraft:single_option}: a button cycling through options.
	 */
	SINGLE_OPTION,
	/**
	 * {@code minecraft:number_range}: a slider.
	 */
	NUMBER_RANGE,
	/**
	 * An input type the protocol does not model.
	 */
	OTHER
}
