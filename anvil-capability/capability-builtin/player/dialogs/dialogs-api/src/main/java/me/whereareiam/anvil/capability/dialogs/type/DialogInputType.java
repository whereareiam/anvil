package me.whereareiam.anvil.capability.dialogs.type;

/**
 * Kind of a dialog input and the value it holds.
 */
public enum DialogInputType {
	/**
	 * A text field; its value is the text.
	 */
	TEXT,
	/**
	 * A checkbox; its value is {@code true} or {@code false}.
	 */
	BOOLEAN,
	/**
	 * A button cycling through options; its value is the ID of the selected option.
	 */
	SINGLE_OPTION,
	/**
	 * A slider; its value is a number between the input's start and end.
	 */
	NUMBER_RANGE,
	/**
	 * An input type this Anvil version does not know; it cannot be given a value.
	 */
	OTHER
}
