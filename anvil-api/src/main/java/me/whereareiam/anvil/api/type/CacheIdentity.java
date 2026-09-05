package me.whereareiam.anvil.api.type;

/**
 * Selects which scenario inputs distinguish a workspace cache snapshot.
 */
public enum CacheIdentity {
	/**
	 * Includes the process, distribution, and installed assets. This is the default for state
	 * whose compatibility depends on the plugin or configuration under test.
	 */
	PROCESS_AND_ASSETS,
	/**
	 * Includes the process and distribution, but ignores installed asset changes. Use only for
	 * caches such as dependency downloads whose contents are independently keyed and validated
	 * by their owner. An explicit cache key can further separate dependency sets.
	 */
	PROCESS
}
