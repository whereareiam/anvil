package me.whereareiam.anvil.api.type;

/**
 * Defines when a process workspace path is deleted.
 */
public enum CleanupPhase {
	/** Delete the path before assets and caches are restored. */
	BEFORE_START,
	/** Delete the path after the process has stopped normally or unsuccessfully. */
	AFTER_STOP,
	/** Delete the path only when startup or scenario setup failed. */
	ON_FAILURE
}
