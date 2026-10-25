package me.whereareiam.anvil.workbench.remote.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.BoundExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.workbench.protocol.model.execution.OpenSession;
import me.whereareiam.anvil.workbench.protocol.model.workbench.OfferedExecution;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.function.Supplier;

/**
 * One execution provider a workbench on another machine offers. Its sessions open there with the workbench's own
 * ports, images and Java runtimes; the engine's context travels as it is.
 */
@RequiredArgsConstructor
public final class RemoteExecutionProvider implements BoundExecutionProvider {
	private final @NotNull WorkbenchConnection connection;
	private final @NotNull OfferedExecution offered;
	private final @NotNull Supplier<@Nullable String> unavailable;
	private final @NotNull InetAddress mirrorAddress;

	@Override
	public @NotNull String id() {
		return offered.getId();
	}

	@Override
	public boolean preservesClientAddress() {
		return offered.isPreservesClientAddress();
	}

	@Override
	public boolean connectsEnvironments() {
		return offered.isConnectsEnvironments();
	}

	@Override
	public @Nullable String unavailableReason() {
		return unavailable.get();
	}

	@Override
	public @NotNull ExecutionSession open(@NotNull ExecutionContext context) {
		return new RemoteExecutionSession(connection, connection.call(new OpenSession(offered.getId(), context)).getHandle(), mirrorAddress);
	}
}
