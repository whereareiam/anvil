package me.whereareiam.anvil.api.type;

/**
 * Selects whether a process receives a fresh or reusable workspace.
 */
public enum WorkspaceMode {
	/** Create a unique process workspace for every scenario start. */
	FRESH,
	/** Reuse a scenario/process workspace while holding an exclusive lock. */
	PERSISTENT
}
