package me.whereareiam.anvil.api.scenario;

/**
 * Owns one active scenario execution and exposes its runtime services.
 */
public interface ScenarioContext extends ScenarioAccess, AutoCloseable {
	/**
	 * Releases the scenario using the caller's outcome for persistence and diagnostics.
	 * Earlier lifecycle failures still prevent successful finalization.
	 * Every owned cleanup is attempted; secondary failures are suppressed on the first.
	 * Repeated finalization does not release resources again.
	 *
	 * @param successful whether caller work completed normally
	 */
	void finish(boolean successful);

	/**
	 * Finalizes normally completed caller work. Use {@link #finish(boolean)} with false
	 * when caller work failed so persistence and diagnostic policies receive that outcome.
	 */
	@Override
	default void close() {
		finish(true);
	}
}
