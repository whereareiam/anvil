package me.whereareiam.anvil.integration.intellij.type;

/**
 * Effective lifecycle of an IDE environment, including its processes and setup.
 * A running session may still have stopped processes or pending setup.
 */
public enum EnvironmentState {
	/**
	 * No environment has been started.
	 */
	NOT_STARTED,
	/**
	 * The session or one of its processes is starting.
	 */
	STARTING,
	/**
	 * The session is available but no process is ready.
	 */
	READY_TO_START,
	/**
	 * At least one, but not every configured process is ready.
	 */
	PARTIALLY_RUNNING,
	/**
	 * All configured processes are ready while scenario setup remains pending.
	 */
	SETUP_PENDING,
	/**
	 * The configured environment is ready for interaction.
	 */
	RUNNING,
	/**
	 * The environment is releasing its resources.
	 */
	STOPPING,
	/**
	 * Environment cleanup completed.
	 */
	STOPPED,
	/**
	 * Environment startup or cleanup failed.
	 */
	FAILED
}
