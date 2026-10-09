package me.whereareiam.anvil.api.type;

/**
 * Selects which scenario inputs distinguish the snapshots of a workspace cache.
 */
public enum CacheIdentity {
	/**
	 * The process selection and its installed assets. A changed plugin or configuration file starts a
	 * new snapshot, which suits state whose validity depends on what is under test.
	 */
	PROCESS_AND_ASSETS,
	/**
	 * The process selection only. Changed assets keep the snapshot, which suits downloads such as a
	 * plugin's dependency libraries, whose owner validates them itself. Use an explicit cache key to
	 * separate dependency sets further.
	 */
	PROCESS
}
