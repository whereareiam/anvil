package me.whereareiam.anvil.launcher;

import me.whereareiam.anvil.api.engine.EngineBuilder;
import me.whereareiam.anvil.api.engine.EngineExtension;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.engine.AnvilEngineBuilder;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.launcher.assembly.LauncherAssembly;
import me.whereareiam.anvil.launcher.config.EngineDefaults;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Constructs the default scenario factory and transfers its services before installing caller extensions.
 */
final class LauncherBuilder implements EngineBuilder {
	private final @NotNull List<ExecutionProvider> executions;

	private final List<EngineExtension> extensions = new ArrayList<>();
	private @NotNull EngineOptions options = EngineOptions.builder().build();
	private boolean consumed;

	LauncherBuilder(@NotNull List<ExecutionProvider> executions) {
		this.executions = List.copyOf(executions);
	}

	@Override
	public synchronized @NotNull EngineBuilder options(@NotNull EngineOptions options) {
		ensureOpen();
		this.options = options;

		return this;
	}

	@Override
	public synchronized @NotNull EngineBuilder extension(@NotNull EngineExtension extension) {
		ensureOpen();
		extensions.add(extension);

		return this;
	}

	@Override
	public synchronized @NotNull ScenarioEngine build() {
		ensureOpen();
		consumed = true;
		EngineOptions effective = EngineDefaults.resolve(options);

		try {
			return build(effective, new LauncherAssembly(effective, executions));
		} finally {
			extensions.clear();
		}
	}

	private ScenarioEngine build(EngineOptions effective, LauncherAssembly assembly) {
		try {
			EngineBuilder engine = new AnvilEngineBuilder(assembly.getScenarioFactory()).options(effective)
					.extension(registration -> registration.own(assembly));
			extensions.forEach(engine::extension);

			return engine.build();
		} catch (RuntimeException | Error failure) {
			try (assembly) {
				throw failure;
			}
		}
	}

	private void ensureOpen() {
		if (consumed) throw new IllegalStateException("Launcher builder has already been consumed");
	}
}
