package me.whereareiam.anvil.api.type;

/**
 * Controls how a declared workspace cache is restored and saved.
 */
public enum CachePolicy {
	/** Restore an existing cache and save the workspace path after a successful run. */
	RESTORE_AND_SAVE,
	/** Restore an existing cache but never overwrite it. */
	RESTORE_ONLY,
	/** Do not restore a cache, but save the workspace path after a successful run. */
	SAVE_ONLY,
	/** Disable a matching provider-default cache. */
	DISABLED
}
