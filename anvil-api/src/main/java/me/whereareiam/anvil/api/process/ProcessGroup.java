package me.whereareiam.anvil.api.process;


/**
 * Owns a scenario's prepared process targets, generations, agent connections, and workspaces.
 * Close players before finalizing this group. A failed restart prevents successful finalization.
 */
public interface ProcessGroup extends ScenarioProcesses, AutoCloseable {
	/**
	 * Releases every owned resource and records the run outcome for workspace retention.
	 *
	 * @param successful whether scenario setup and caller work completed successfully
	 */
	void finish(boolean successful);

	/**
	 * Finalizes a normally completed group; previously recorded failures still take precedence.
	 */
	@Override
	default void close() {
		finish(true);
	}
}
