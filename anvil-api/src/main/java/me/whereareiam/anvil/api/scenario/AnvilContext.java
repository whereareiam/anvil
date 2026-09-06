package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import org.jetbrains.annotations.NotNull;

/**
 * Running scenario supplied to tests, setup hooks, and manual runners.
 */
public interface AnvilContext extends AutoCloseable {
	/**
	 * Returns the scenario that produced this context.
	 *
	 * @return running scenario
	 */
	@NotNull AnvilScenario scenario();

	/**
	 * Returns the scenario-owned process lookup and lifecycle operations.
	 *
	 * @return scenario processes
	 */
	@NotNull ScenarioProcesses processes();

	/**
	 * Returns the context-owned simulated-player factory and registry.
	 *
	 * @return player manager
	 */
	@NotNull PlayerManager players();

	/**
	 * Destroys players and stops every process in reverse launch order.
	 */
	@Override
	void close();
}
