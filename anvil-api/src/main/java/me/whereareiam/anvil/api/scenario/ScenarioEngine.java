package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns scenario execution and shared resources across one or more scenario sessions.
 * Each returned context may be closed independently; closing the engine releases remaining contexts.
 */
public interface ScenarioEngine extends AutoCloseable {
	/**
	 * Validates and prepares a scenario's complete topology without starting its processes or setup hook.
	 * Prepared workspaces, shared inputs, and listener addresses remain owned by the returned context.
	 * Per-execution configuration and launch resources are created only when a process starts.
	 *
	 * @param scenario scenario declaration
	 * @return context ready for individual process starts or {@link ScenarioContext#start()}
	 * @throws IllegalStateException if the engine is closed
	 * @throws ScenarioValidationException if the declaration is invalid
	 * @throws ProvisioningException if preparation fails
	 */
	default @NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario) {
		return prepare(scenario, null);
	}

	/**
	 * Prepares the complete scenario while retaining an optional observer for future process generations.
	 * No process generation is created until that process is started. Preparation failures release acquired resources.
	 *
	 * @param scenario scenario declaration
	 * @param observer optional observer; callbacks for independent process starts may run concurrently
	 * @return context owning the prepared topology
	 * @throws IllegalStateException if the engine is closed
	 * @throws ScenarioValidationException if the declaration is invalid
	 * @throws ProvisioningException if preparation fails
	 */
	@NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer);

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
	default @NotNull ScenarioContext start(@NotNull AnvilScenario scenario) {
		return start(scenario, null);
	}

	/**
	 * Validates and starts a scenario while exposing its process generations before readiness.
	 * The observer remains attached to the returned context and receives replacement generations
	 * when its processes restart. Observer failures participate in the same startup rollback as
	 * provisioning and process failures; observed handles remain available for diagnostic reads.
	 *
	 * @param scenario scenario declaration
	 * @param observer optional observer; callbacks for independent processes may run concurrently
	 * @return context after readiness and setup complete
	 * @throws IllegalStateException if the engine is closed
	 * @throws ScenarioValidationException if the declaration is invalid
	 * @throws ProvisioningException if preparation fails
	 * @throws ProcessException if a process fails to start
	 * @throws ScenarioStartupException if the setup hook fails
	 */
	default @NotNull ScenarioContext start(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) {
		ScenarioContext context = prepare(scenario, observer);
		context.start();
		return context;
	}

	/**
	 * Closes remaining contexts and shared engine resources. Repeated calls have no effect.
	 * Cleanup attempts all owned resources and preserves secondary failures as suppressed exceptions.
	 * A startup callback must return or fail before its caller closes the parent engine.
	 *
	 * @throws IllegalStateException when called synchronously from a scenario startup callback
	 */
	@Override
	void close();
}
