package me.whereareiam.anvil.integration.intellij.type;

/**
 * Lifecycle of the selected source's scenario catalog.
 */
public enum CatalogState {
	/**
	 * No catalog has been loaded for the selected source.
	 */
	NOT_LOADED,
	/**
	 * Source preparation or scenario discovery is in progress.
	 */
	LOADING,
	/**
	 * Scenario discovery completed successfully.
	 */
	READY,
	/**
	 * Preparation or discovery failed.
	 */
	FAILED
}
