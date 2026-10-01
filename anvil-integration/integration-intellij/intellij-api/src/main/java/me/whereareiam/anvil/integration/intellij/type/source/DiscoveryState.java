package me.whereareiam.anvil.integration.intellij.type.source;

/**
 * Current operation performed while locating and importing scenario sources.
 */
public enum DiscoveryState {
	/**
	 * No discovery or sync operation is in progress.
	 */
	IDLE,
	/**
	 * Imported source metadata is being inspected.
	 */
	DETECTING,
	/**
	 * Native project sync is in progress.
	 */
	SYNCING
}
