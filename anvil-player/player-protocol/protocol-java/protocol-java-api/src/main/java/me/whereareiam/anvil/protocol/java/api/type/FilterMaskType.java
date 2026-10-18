package me.whereareiam.anvil.protocol.java.api.type;

/**
 * How much of a player's chat message the server's text filter hides from the receiving client, named as the game's
 * filter mask types.
 */
public enum FilterMaskType {
	/**
	 * Nothing is hidden.
	 */
	PASS_THROUGH,
	/**
	 * The whole message is hidden, so the client does not show it.
	 */
	FULLY_FILTERED,
	/**
	 * Some characters are hidden; the client shows them as {@code #}.
	 */
	PARTIALLY_FILTERED
}
