package me.whereareiam.anvil.api.scenario;

/**
 * Owns one scenario extension's resources and receives its final success or failure outcome.
 */

public interface ScenarioAttachment extends AutoCloseable {
	/**
	 * Finalizes this contribution before the scenario's processes and players are released.
	 *
	 * @param successful whether setup, caller work, and preceding cleanup succeeded
	 */
	void finish(boolean successful);

	/**
	 * Finalizes a contribution whose caller completed normally.
	 */
	@Override
	default void close() {
		finish(true);
	}
}
