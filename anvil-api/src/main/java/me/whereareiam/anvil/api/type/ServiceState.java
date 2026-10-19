package me.whereareiam.anvil.api.type;

/**
 * What a scenario's service is doing.
 */
public enum ServiceState {
	/** Its container starts and is not ready yet. */
	STARTING,
	/** It runs and is ready. */
	RUNNING,
	/** Its container is paused: it keeps its state but answers nobody until resumed. */
	PAUSED,
	/** Its container stopped or was killed. */
	STOPPED
}
