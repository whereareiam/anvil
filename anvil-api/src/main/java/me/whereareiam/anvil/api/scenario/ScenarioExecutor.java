package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a structurally validated scenario through installed scoped services.
 * The engine owns global validation, extension attachment, and the scenario setup hook.
 */

public interface ScenarioExecutor {
	/**
	 * Validates domain-specific requirements and acquires a ready scenario context.
	 * A failing call must release resources not transferred through its return value.
	 *
	 * @param scenario structurally valid declaration
	 * @return owned context before global extensions and setup execute
	 */
	@NotNull ScenarioContext open(@NotNull AnvilScenario scenario);
}
