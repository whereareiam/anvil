package me.whereareiam.anvil.environment.execution.local;

import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.runtime.LocalRuntimePreparation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Executes scenario workloads directly on the current host.
 */
public final class LocalExecutionProvider implements ExecutionProvider {
	private final @NotNull LocalExecutionSettings settings;
	private final @Nullable LocalRuntimePreparation runtime;

	public LocalExecutionProvider() {
		this(LocalExecutionSettings.builder().build(), null);
	}

	public LocalExecutionProvider(@NotNull LocalRuntimePreparation runtime) {
		this(LocalExecutionSettings.builder().build(), runtime);
	}

	public LocalExecutionProvider(@NotNull LocalExecutionSettings settings, @Nullable LocalRuntimePreparation runtime) {
		this.settings = settings;
		this.runtime = runtime;
	}

	@Override
	public @NotNull String id() {
		return "local";
	}

	@Override
	public @NotNull ExecutionSession open(@NotNull ExecutionContext context) {
		LocalRuntimePreparation selected = runtime == null ? context.getLocalRuntime() : runtime;
		return new LocalExecutionSession(context, settings, selected);
	}
}
