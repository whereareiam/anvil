package me.whereareiam.anvil.api.engine;

import me.whereareiam.anvil.api.scenario.ScenarioExecutor;
import me.whereareiam.anvil.api.scenario.ScenarioExtension;
import org.jetbrains.annotations.NotNull;

/**
 * Accepts global lifecycle contributions without exposing scoped service lookup or implementation types.
 * Registrations remain open only while the engine is being assembled.
 */
public interface EngineRegistration {
	/**
	 * Selects the scenario execution boundary. Exactly one executor is required.
	 *
	 * @param executor scenario resource factory, borrowed until engine shutdown
	 * @throws IllegalStateException when an executor was already registered
	 */
	void executor(@NotNull ScenarioExecutor executor);

	/**
	 * Adds a scenario contribution, installed in registration order before the scenario setup hook.
	 * Returned attachments finalize in reverse order before the scoped context is released.
	 *
	 * @param extension factory for scenario-owned lifecycle additions
	 */
	void scenarios(@NotNull ScenarioExtension extension);

	/**
	 * Transfers a shared resource to this assembly. Resources close in reverse registration order
	 * after all scenarios, including when a later installation or engine construction fails.
	 *
	 * @param resource resource owned after this call returns
	 * @throws IllegalArgumentException when the same resource instance was already transferred
	 */
	void own(@NotNull AutoCloseable resource);
}
