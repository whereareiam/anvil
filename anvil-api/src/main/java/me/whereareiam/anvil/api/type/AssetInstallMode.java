package me.whereareiam.anvil.api.type;

/**
 * Controls when an asset is installed into a process workspace.
 */
public enum AssetInstallMode {
	/** Install the asset when a persistent workspace has not seeded it yet. */
	SEED_ONCE,
	/** Install or replace the asset before every process start. */
	ALWAYS
}
