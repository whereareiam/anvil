package me.whereareiam.anvil.api.scenario;

/**
 * Owns one prepared or running scenario execution and exposes its runtime services.
 */
public interface ScenarioContext extends ScenarioAccess, AutoCloseable {
	/**
	 * Creates fresh launch resources for remaining prepared processes and starts them in dependency order.
	 * After readiness, installs global extensions and executes setup once.
	 * Repeated calls start stopped processes without repeating completed global initialization.
	 * Failed full startup finalizes the context with an unsuccessful outcome.
	 */
	void start();

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
