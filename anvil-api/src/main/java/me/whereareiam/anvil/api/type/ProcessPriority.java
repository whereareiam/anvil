package me.whereareiam.anvil.api.type;

/**
 * Selects how launched processes compete for the machine's processors.
 */
public enum ProcessPriority {
	/** Compete like any other program the user starts. */
	NORMAL,
	/** Yield the processors to other work on the machine, so it stays responsive while scenarios run. */
	LOW
}
