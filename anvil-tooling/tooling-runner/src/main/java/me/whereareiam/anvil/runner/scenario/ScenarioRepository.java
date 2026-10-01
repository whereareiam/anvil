package me.whereareiam.anvil.runner.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Discovers one-definition-per-class scenarios from a prepared project runtime.
 *
 * <p>Build integrations generate {@link AnvilScenarioDefinition#INDEX_RESOURCE} after compiling
 * a project's scenario source set. The index contains class names only, so Gradle import never
 * evaluates user code. A standard Java service descriptor is accepted as a fallback for non-Gradle
 * hosts and embedded applications. Definitions are instantiated exactly once by this repository;
 * the evaluated scenario and its presentation metadata are then reused by the runner.</p>
 */
public final class ScenarioRepository {
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
			names.computeIfAbsent(entry.scenario().getName(), ignored -> new ArrayList<>()).add(entry);
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
			entries.add(new Entry("embedded:" + scenario.getName(), scenario));

		return new ScenarioRepository(entries);
	}

	/**
	 * Discovers definitions from the current thread context class loader.
	 *
	 * @return repository containing all indexed or service-loaded definitions
	 * @throws ReflectiveOperationException when a definition cannot be constructed
	 */
	public static @NotNull ScenarioRepository discover() throws ReflectiveOperationException {
		return load(List.of());
	}

	/**
	 * Loads the supplied definition classes. An empty list reads the generated index and then the
	 * Java service descriptor fallback.
	 *
	 * @param definitionNames fully qualified definition class names; empty to discover them
	 * @return evaluated repository
	 * @throws ReflectiveOperationException when a class is missing or has no usable constructor
	 */
	public static @NotNull ScenarioRepository load(@NotNull List<String> definitionNames)
			throws ReflectiveOperationException {
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader loader = contextLoader == null ? ScenarioRepository.class.getClassLoader() : contextLoader;
		List<String> names = definitionNames.isEmpty() ? discoverNames(loader) : normalize(definitionNames);
		List<Entry> entries = new ArrayList<>(names.size());
		for (String name : names) entries.add(evaluate(name, loader));

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

	private static @NotNull Entry evaluate(@NotNull String name, @NotNull ClassLoader loader)
			throws ReflectiveOperationException {
		Class<?> raw = Class.forName(name, false, loader);
		Class<? extends AnvilScenarioDefinition> definitionClass = raw.asSubclass(AnvilScenarioDefinition.class);
		AnvilScenarioDefinition definition = definitionClass.getDeclaredConstructor().newInstance();
		AnvilScenario scenario = definition.define();
		if (scenario.getName().isBlank())
			throw new IllegalArgumentException("Scenario name must not be blank: " + name);

		return new Entry(name, scenario);
	}

	private static @NotNull List<String> discoverNames(@NotNull ClassLoader loader) {
		Set<String> names = new LinkedHashSet<>();
		try {
			Enumeration<URL> resources = loader.getResources(AnvilScenarioDefinition.INDEX_RESOURCE);
			while (resources.hasMoreElements()) readIndex(resources.nextElement(), names);
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read scenario definition index", failure);
		}

		try {
			ServiceLoader.load(AnvilScenarioDefinition.class, loader).stream()
					.map(ServiceLoader.Provider::type)
					.map(Class::getName)
					.forEach(names::add);
		} catch (ServiceConfigurationError failure) {
			throw new IllegalStateException("Could not read scenario definition service descriptor", failure);
		}

		return names.stream().sorted().toList();
	}

	private static void readIndex(@NotNull URL resource, @NotNull Set<String> names) {
		try (InputStream input = resource.openStream()) {
			String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			for (String line : content.lines().toList()) {
				String name = line.strip();
				if (!name.isEmpty() && !name.startsWith("#")) names.add(name);
			}
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read scenario definition index " + resource, failure);
		}
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
