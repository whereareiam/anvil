package me.whereareiam.anvil.protocol.java.api.type;

/**
 * How much text a {@code brigadier:string} argument reads, as Brigadier's string types name it.
 */
public enum StringArgumentMode {
	/**
	 * One word without spaces.
	 */
	SINGLE_WORD,
	/**
	 * One word, or a quoted phrase that may hold spaces.
	 */
	QUOTABLE_PHRASE,
	/**
	 * The rest of the command.
	 */
	GREEDY_PHRASE
}
