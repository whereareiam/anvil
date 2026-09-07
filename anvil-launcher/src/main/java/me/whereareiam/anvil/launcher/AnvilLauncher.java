package me.whereareiam.anvil.launcher;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.engine.AnvilEngine;
import me.whereareiam.anvil.engine.EngineDefaults;
import me.whereareiam.anvil.provisioning.cache.ArtifactCache;
import me.whereareiam.anvil.provisioning.java.JavaRuntimeResolver;
import me.whereareiam.anvil.execution.api.ExecutionProvider;
import java.util.List;
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
		return create(options, new ExecutionProvider[0]);
	}

	/** Creates an engine with explicitly supplied execution providers. */
	public static @NotNull ScenarioEngine create(@NotNull EngineOptions options, @NotNull ExecutionProvider... executionProviders) {
		EngineOptions effective = EngineDefaults.resolve(options);
		ArtifactCache artifacts = new ArtifactCache(effective.getCacheDirectory(), effective.isOffline(), effective.isRefresh(), effective.getDownloadParallelism());

		try {
			JavaRuntimeResolver java = new JavaRuntimeResolver(
					effective.getCacheDirectory(),
					artifacts,
					effective.isDownloadJava(),
					effective.isRefresh()
			);

			return new AnvilEngine(effective, artifacts, java, List.of(executionProviders));
		} catch (RuntimeException | Error failure) {
			artifacts.close();
			throw failure;
		}
	}
}
