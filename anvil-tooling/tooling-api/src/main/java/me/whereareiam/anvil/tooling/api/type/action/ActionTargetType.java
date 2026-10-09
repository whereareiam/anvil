package me.whereareiam.anvil.tooling.api.type.action;

/**
 * Kinds of runtime targets that can expose tooling actions and observations.
 */
public enum ActionTargetType {
	/**
	 * The whole prepared environment.
	 */
	SCENARIO,
	/**
	 * One current server or proxy process.
	 */
	PROCESS,
	/**
	 * One registered simulated player.
	 */
	PLAYER
}
