package me.whereareiam.anvil.api.type;

/**
 * Selects how long a server or proxy keeps running.
 */
public enum ProcessLifetime {
	/** Start the process with its scenario and stop it when the scenario finishes. */
	SCENARIO,
	/**
	 * Keep the process running when its scenario finishes and hand it to the next scenario of the same engine
	 * that declares the same process. Only one scenario uses it at a time, and it stops when the engine closes.
	 */
	ENGINE
}
