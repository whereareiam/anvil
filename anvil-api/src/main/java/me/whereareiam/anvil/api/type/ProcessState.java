package me.whereareiam.anvil.api.type;

/**
 * Observable lifecycle state of a managed server or proxy process.
 */
public enum ProcessState {
	/**
	 * The process has not been launched.
	 */
	CREATED,
	/**
	 * The operating-system process is starting.
	 */
	STARTING,
	/**
	 * The process passed its readiness checks.
	 */
	READY,
	/**
	 * Graceful shutdown is in progress.
	 */
	STOPPING,
	/**
	 * The process has exited.
	 */
	STOPPED,
	/**
	 * The process exited or timed out unexpectedly.
	 */
	FAILED
}
