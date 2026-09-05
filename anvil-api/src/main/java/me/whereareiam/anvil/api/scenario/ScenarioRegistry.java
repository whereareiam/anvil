package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Mutable registration target that exposes immutable scenario views to runners.
 */
public final class ScenarioRegistry {
	private final Map<String, AnvilScenario> scenarios = new LinkedHashMap<>();
	private final Map<String, ScenarioGroup> groups = new LinkedHashMap<>();

	/**
	 * Registers a scenario.
	 *
	 * @param scenario scenario definition
	 * @return this registry
	 */
	public @NotNull ScenarioRegistry scenario(@NotNull AnvilScenario scenario) {
		if (scenarios.putIfAbsent(scenario.getName(), scenario) != null)
			throw new IllegalArgumentException("Duplicate scenario: " + scenario.getName());

		return this;
	}

	/**
	 * Creates and registers a typed scenario definition.
	 *
	 * @param definition definition to evaluate immediately
	 * @return this registry
	 */
	public @NotNull ScenarioRegistry scenario(@NotNull AnvilScenarioDefinition definition) {
		return scenario(definition.define());
	}

	/**
	 * Registers a scenario group.
	 *
	 * @param group group definition
	 * @return this registry
	 */
	public @NotNull ScenarioRegistry group(@NotNull ScenarioGroup group) {
		if (groups.putIfAbsent(group.getName(), group) != null)
			throw new IllegalArgumentException("Duplicate scenario group: " + group.getName());

		return this;
	}

	/**
	 * Resolves a scenario by name.
	 *
	 * @param name registered name
	 * @return matching scenario
	 * @throws NoSuchElementException when no scenario has that name
	 */
	public @NotNull AnvilScenario requireScenario(@NotNull String name) {
		AnvilScenario scenario = scenarios.get(name);
		if (scenario == null)
			throw new NoSuchElementException("Unknown scenario '" + name + "'. Available: " + scenarios.keySet());

		return scenario;
	}

	/**
	 * Returns scenarios in deterministic registration order.
	 *
	 * @return registered scenarios
	 */
	public @NotNull Collection<AnvilScenario> scenarios() {
		return Collections.unmodifiableCollection(scenarios.values());
	}

	/**
	 * Returns groups in deterministic registration order.
	 *
	 * @return registered scenario groups
	 */
	public @NotNull Collection<ScenarioGroup> groups() {
		return Collections.unmodifiableCollection(groups.values());
	}
}
