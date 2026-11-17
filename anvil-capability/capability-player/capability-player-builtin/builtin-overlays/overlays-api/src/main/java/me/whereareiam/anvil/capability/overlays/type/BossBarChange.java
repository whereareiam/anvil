package me.whereareiam.anvil.capability.overlays.type;

/**
 * What happened to a boss bar.
 */
public enum BossBarChange {
	/**
	 * The server showed the bar to the player.
	 */
	ADDED,
	/**
	 * The server changed the bar's text, progress, style or flags.
	 */
	UPDATED,
	/**
	 * The bar is no longer shown: the server removed it, or the connection that showed it ended.
	 */
	REMOVED
}
