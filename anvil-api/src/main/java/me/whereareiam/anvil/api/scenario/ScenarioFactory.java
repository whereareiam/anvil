package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Prepares a structurally validated scenario through installed scoped services.
 * The engine owns global validation, extension attachment, and the scenario setup hook.
 */
public interface ScenarioFactory {
	/**
	 * Validates domain-specific requirements and acquires the complete unstarted scenario topology.
	 * A failing call must release resources not transferred through its return value.
	 *
	 * @param scenario structurally valid declaration
	 * @param observer optional process-generation observer retained across starts and restarts
	 * @return owned prepared context whose start operation establishes readiness before global initialization
	 */
	@NotNull ScenarioContext create(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer);
}
