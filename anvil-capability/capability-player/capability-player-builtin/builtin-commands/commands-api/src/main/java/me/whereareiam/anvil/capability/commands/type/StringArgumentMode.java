package me.whereareiam.anvil.capability.commands.type;

/**
 * How a {@code brigadier:string} argument reads its value.
 */
public enum StringArgumentMode {
	/**
	 * One word without spaces.
	 */
	SINGLE_WORD,
	/**
	 * One word, or a phrase in quotes.
	 */
	QUOTABLE_PHRASE,
	/**
	 * The rest of the input.
	 */
	GREEDY_PHRASE
}
