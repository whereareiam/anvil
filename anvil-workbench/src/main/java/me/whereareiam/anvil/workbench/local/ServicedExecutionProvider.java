package me.whereareiam.anvil.workbench.local;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.BoundExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionServices;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An execution provider of the local workbench, bound to the workbench's own services.
 */
@RequiredArgsConstructor
final class ServicedExecutionProvider implements BoundExecutionProvider {
	private final @NotNull ExecutionProvider provider;
	private final @NotNull ExecutionServices services;

	@Override
	public @NotNull String id() {
		return provider.id();
	}

	@Override
	public boolean preservesClientAddress() {
		return provider.preservesClientAddress();
	}

	@Override
	public boolean connectsEnvironments() {
		return provider.connectsEnvironments();
	}

	@Override
	public @Nullable String unavailableReason() {
		return provider.unavailableReason();
	}

	@Override
	public @NotNull ExecutionSession open(@NotNull ExecutionContext context) {
		return provider.open(context, services);
	}
}
