package me.whereareiam.anvil.runner.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.TreeMap;

/**
 * Discovers one-definition-per-class scenarios from a prepared project runtime.
 *
 * <p>Definitions are the {@link AnvilScenarioDefinition} service providers of the project runtime,
 * which {@link ServiceLoader} finds in
 * {@code META-INF/services/me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition}. Build
 * integrations write that file after compiling a project's scenario source set; it names classes
 * only, so Gradle import never evaluates user code. A definition's identity is its class's binary
 * name. Only the selected definitions are created, each exactly once by this repository; the
 * evaluated scenario and its presentation metadata are then reused by the runner.</p>
 */
public final class ScenarioRepository {
	private static final String SERVICE_FILE = "META-INF/services/" + AnvilScenarioDefinition.class.getName();

	private final Map<String, Entry> byDefinition;
	private final Map<String, List<Entry>> byName;

	/**
	 * Creates a repository from already evaluated definitions.
	 *
	 * @param entries evaluated definition entries
	 */
	public ScenarioRepository(@NotNull List<Entry> entries) {
		Map<String, Entry> definitions = new LinkedHashMap<>();
		Map<String, List<Entry>> names = new LinkedHashMap<>();
		for (Entry entry : entries) {
			if (definitions.putIfAbsent(entry.definition(), entry) != null)
				throw new IllegalArgumentException("Duplicate scenario definition: " + entry.definition());
			names.computeIfAbsent(entry.scenario().name(), ignored -> new ArrayList<>()).add(entry);
		}
		this.byDefinition = Collections.unmodifiableMap(definitions);
		Map<String, List<Entry>> immutableNames = new LinkedHashMap<>();
		names.forEach((name, values) -> immutableNames.put(name, List.copyOf(values)));
		this.byName = Collections.unmodifiableMap(immutableNames);
	}

	/**
	 * Creates a repository for embedding hosts that already own evaluated scenario values.
	 *
	 * @param scenarios scenarios to expose; each name becomes its stable embedded identity
	 * @return repository containing the supplied scenarios
	 */
	public static @NotNull ScenarioRepository fromScenarios(@NotNull List<AnvilScenario> scenarios) {
		List<Entry> entries = new ArrayList<>(scenarios.size());
		for (AnvilScenario scenario : scenarios)
			entries.add(new Entry("embedded:" + scenario.name(), scenario));

		return new ScenarioRepository(entries);
	}

	/**
	 * Discovers and evaluates every definition registered with the current thread context class loader.
	 *
	 * @return repository containing all registered definitions
	 * @throws IllegalStateException when a registered definition cannot be loaded or created
	 */
	public static @NotNull ScenarioRepository discover() {
		return load(List.of());
	}

	/**
	 * Evaluates the selected registered definitions. An empty list selects every registered definition.
	 *
	 * @param definitionNames binary names of definition classes; empty to select all
	 * @return evaluated repository
	 * @throws IllegalArgumentException when a selected class is not a registered definition
	 * @throws IllegalStateException when a registered definition cannot be loaded or created
	 */
	public static @NotNull ScenarioRepository load(@NotNull List<String> definitionNames) {
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader loader = contextLoader == null ? ScenarioRepository.class.getClassLoader() : contextLoader;
		Map<String, Provider<AnvilScenarioDefinition>> registered = registered(loader);
		List<String> names = definitionNames.isEmpty() ? List.copyOf(registered.keySet()) : normalize(definitionNames);
		List<Entry> entries = new ArrayList<>(names.size());
		for (String name : names) {
			Provider<AnvilScenarioDefinition> provider = registered.get(name);
			if (provider == null)
				throw new IllegalArgumentException("Scenario definition " + name + " is not registered in " + SERVICE_FILE
						+ ". Registered: " + registered.keySet());

			entries.add(evaluate(name, provider));
		}

		return new ScenarioRepository(entries);
	}

	/**
	 * Returns all discovered scenarios in deterministic definition order.
	 *
	 * @return immutable scenario descriptors
	 */
	public @NotNull List<ScenarioDescriptor> scenarios() {
		return byDefinition.values().stream()
				.map(entry -> ScenarioDescriptorFactory.describe(entry.definition(), entry.scenario()))
				.toList();
	}

	/**
	 * Resolves either a definition class name or a unique scenario name.
	 *
	 * @param selection definition class name or scenario name
	 * @return matching evaluated entry
	 * @throws NoSuchElementException when no definition or scenario matches
	 * @throws IllegalArgumentException when a scenario name is ambiguous
	 */
	public @NotNull Entry require(@NotNull String selection) {
		Entry direct = byDefinition.get(selection);
		if (direct != null) return direct;
		List<Entry> matches = byName.get(selection);
		if (matches == null || matches.isEmpty())
			throw new NoSuchElementException("Unknown scenario definition or name '" + selection + "'. Available: "
					+ byDefinition.keySet());
		if (matches.size() > 1)
			throw new IllegalArgumentException("Scenario name '" + selection + "' is ambiguous; select one of: "
					+ matches.stream().map(Entry::definition).toList());

		return matches.getFirst();
	}

	/**
	 * Returns evaluated entries for embedding hosts that need the definition identity and model.
	 *
	 * @return immutable entries in discovery order
	 */
	public @NotNull List<Entry> entries() {
		return List.copyOf(byDefinition.values());
	}

	private static @NotNull Entry evaluate(@NotNull String name, @NotNull Provider<AnvilScenarioDefinition> provider) {
		AnvilScenarioDefinition definition;
		try {
			definition = provider.get();
		} catch (ServiceConfigurationError failure) {
			throw new IllegalStateException("Could not create scenario definition " + name, failure);
		}

		AnvilScenario scenario = definition.define();
		if (scenario.name().isBlank())
			throw new IllegalArgumentException("Scenario name must not be blank: " + name);

		return new Entry(name, scenario);
	}

	private static @NotNull Map<String, Provider<AnvilScenarioDefinition>> registered(@NotNull ClassLoader loader) {
		Map<String, Provider<AnvilScenarioDefinition>> providers = new TreeMap<>();
		try {
			ServiceLoader.load(AnvilScenarioDefinition.class, loader).stream()
					.forEach(provider -> providers.put(provider.type().getName(), provider));
		} catch (ServiceConfigurationError | LinkageError failure) {
			throw new IllegalStateException("Could not load the scenario definitions registered in " + SERVICE_FILE, failure);
		}

		return providers;
	}

	private static @NotNull List<String> normalize(@NotNull List<String> names) {
		return names.stream().map(String::strip).filter(name -> !name.isEmpty()).distinct().sorted().toList();
	}

	/**
	 * One definition class and its evaluated scenario declaration.
	 */
	public static final class Entry {
		private final String definition;
		private final AnvilScenario scenario;

		private Entry(@NotNull String definition, @NotNull AnvilScenario scenario) {
			this.definition = definition;
			this.scenario = scenario;
		}

		public @NotNull String definition() {
			return definition;
		}

		public @NotNull AnvilScenario scenario() {
			return scenario;
		}
	}
}
