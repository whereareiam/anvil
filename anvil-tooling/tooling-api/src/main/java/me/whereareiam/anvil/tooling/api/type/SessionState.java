package me.whereareiam.anvil.tooling.api.type;

/**
 * Lifecycle of a foreground environment; RUNNING does not imply a JUnit test has passed.
 */
public enum SessionState {
	/**
	 * No environment has been selected.
	 */
	IDLE,
	/**
	 * Preparing or starting the whole environment or a selected component.
	 */
	STARTING,
	/**
	 * An environment is available for interaction; individual components can still be stopped.
	 */
	RUNNING,
	/**
	 * Releasing the environment and its owned resources.
	 */
	STOPPING,
	/**
	 * Environment execution and cleanup finished without a retained failure.
	 */
	STOPPED,
	/**
	 * A lifecycle failure occurred; cleanup may still be required and diagnostics remain available.
	 */
	FAILED
}
