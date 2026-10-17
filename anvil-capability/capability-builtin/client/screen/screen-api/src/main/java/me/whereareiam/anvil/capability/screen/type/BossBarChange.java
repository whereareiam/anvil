package me.whereareiam.anvil.capability.screen.type;

/**
 * What happened to a boss bar.
 */
public enum BossBarChange {
	/**
	 * The server added the bar to the player's screen.
	 */
	ADDED,
	/**
	 * The server changed the bar's text, progress, style or flags.
	 */
	UPDATED,
	/**
	 * The bar left the player's screen: the server removed it, or the connection that showed it ended.
	 */
	REMOVED
}
