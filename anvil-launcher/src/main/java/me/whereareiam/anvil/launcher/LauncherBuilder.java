package me.whereareiam.anvil.launcher;

import me.whereareiam.anvil.api.engine.EngineBuilder;
import me.whereareiam.anvil.api.engine.EngineExtension;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.engine.AnvilEngineBuilder;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.launcher.assembly.LauncherEngineExtension;
import me.whereareiam.anvil.launcher.config.EngineDefaults;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplies default scoped assembly before caller-provided global extensions are installed.
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
		EngineBuilder engine = new AnvilEngineBuilder().options(effective)
				.extension(new LauncherEngineExtension(effective, executions));
		extensions.forEach(engine::extension);
		extensions.clear();

		return engine.build();
	}

	private void ensureOpen() {
		if (consumed) throw new IllegalStateException("Launcher builder has already been consumed");
	}
}
