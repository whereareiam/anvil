package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

/**
 * Owns scenario execution and shared resources across one or more scenario sessions.
 * Each returned context may be closed independently; closing the engine releases remaining contexts.
 */
public interface ScenarioEngine extends AutoCloseable {
	/**
	 * Validates, provisions, and starts a scenario, including its setup hook.
	 * Failed startup releases acquired scenario resources before returning control to the caller.
	 *
	 * @param scenario scenario declaration
	 * @return context after readiness and setup complete
	 * @throws IllegalStateException if the engine is closed
	 * @throws ScenarioValidationException if the declaration is invalid
	 * @throws ProvisioningException if preparation fails
	 * @throws ProcessException if a process fails to start
	 * @throws ScenarioStartupException if the setup hook fails
	 */
	@NotNull ScenarioContext start(@NotNull AnvilScenario scenario);

	/**
	 * Closes remaining contexts and shared engine resources. Repeated calls have no effect.
	 * Cleanup attempts all owned resources and preserves secondary failures as suppressed exceptions.
	 */
	@Override
	void close();
}
