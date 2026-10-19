package me.whereareiam.anvil.api.service;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * The services of a running scenario.
 */
public interface ScenarioServices {
	/**
	 * The services of a scenario that declares none.
	 */
	ScenarioServices NONE = new ScenarioServices() {
		@Override
		public @NotNull Collection<RunningService> all() {
			return List.of();
		}

		@Override
		public @NotNull RunningService get(@NotNull String name) {
			throw new IllegalArgumentException("The scenario has no service '" + name + "'");
		}
	};

	/**
	 * Returns every service of the scenario.
	 */
	@NotNull Collection<RunningService> all();

	/**
	 * Returns one service of the scenario.
	 *
	 * @param name the service's name
	 * @return the service
	 * @throws IllegalArgumentException when the scenario has no such service
	 */
	@NotNull RunningService get(@NotNull String name);
}
