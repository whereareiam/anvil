package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import org.jetbrains.annotations.NotNull;

/**
 * Access to the declaration and runtime services of an active scenario.
 */
public interface ScenarioAccess {
	/**
	 * Returns the immutable declaration that produced this scenario.
	 *
	 * @return scenario declaration
	 */
	@NotNull AnvilScenario definition();

	/**
	 * Returns the scenario-owned process lookup and lifecycle operations.
	 *
	 * @return scenario processes
	 */
	@NotNull ScenarioProcesses processes();

	/**
	 * Returns the scenario-owned simulated-player factory and registry.
	 *
	 * @return player manager
	 */
	@NotNull PlayerManager players();
}
