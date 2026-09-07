package me.whereareiam.anvil.api.scenario;

/**
 * Owns one active scenario execution and exposes its runtime services.
 */
public interface ScenarioContext extends ScenarioAccess, AutoCloseable {
	/**
	 * Releases players, processes, execution resources, and scenario workspace state.
	 */
	@Override
	void close();
}
