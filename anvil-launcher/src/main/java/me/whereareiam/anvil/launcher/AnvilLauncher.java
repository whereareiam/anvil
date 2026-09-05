package me.whereareiam.anvil.launcher;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.engine.AnvilEngine;
import org.jetbrains.annotations.NotNull;

/**
 * Creates the local scenario engine packaged in the launcher distribution.
 * Platform and protocol implementations are selected from the caller's installed providers.
 */
public final class AnvilLauncher {
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
		return new AnvilEngine(options);
	}
}
