package me.whereareiam.anvil.launcher;

import me.whereareiam.anvil.api.engine.EngineBuilder;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Creates the local scenario engine packaged in the launcher distribution.
 * Platform and protocol implementations are selected from the caller's installed providers.
 */
public final class AnvilLauncher {
	/**
	 * Collects default engine options and additional global extensions without opening resources.
	 *
	 * @return builder that installs the default scoped services when built
	 */
	public static @NotNull EngineBuilder builder() {
		return new LauncherBuilder(List.of());
	}

	/**
	 * Resolves local defaults and discovers the services needed to execute scenarios.
	 * No scenario or protocol backend is started until requested through the returned engine.
	 *
	 * <pre>{@code
	 * try (ScenarioEngine engine = AnvilLauncher.create(options)) {
	 *     try (var context = engine.start(scenario)) {
	 *         // Perform actions while the context is open.
	 *     }
	 * }
	 * }</pre>
	 *
	 * @param options declarative options shared by embedding and tooling
	 * @return caller-owned engine
	 */
	public static @NotNull ScenarioEngine create(@NotNull EngineOptions options) {
		return create(options, new ExecutionProvider[0]);
	}

	/**
	 * Creates an engine with explicitly supplied execution providers.
	 *
	 * @param options declarative options shared by embedding and tooling
	 * @param executionProviders providers that override installed providers with the same IDs
	 * @return caller-owned engine
	 */
	public static @NotNull ScenarioEngine create(@NotNull EngineOptions options, @NotNull ExecutionProvider... executionProviders) {
		return new LauncherBuilder(List.of(executionProviders)).options(options).build();
	}
}
