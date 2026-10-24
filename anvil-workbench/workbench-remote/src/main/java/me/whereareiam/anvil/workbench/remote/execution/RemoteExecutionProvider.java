package me.whereareiam.anvil.workbench.remote.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.function.Supplier;

/**
 * One execution provider of a workbench on another machine. Its sessions run there with the workbench's own
 * ports, images and Java runtimes; from the engine's context only the network policy and priority travel.
 */
@RequiredArgsConstructor
public final class RemoteExecutionProvider implements ExecutionProvider {
	private final @NotNull WorkbenchConnection connection;
	private final @NotNull String id;
	private final boolean preservesClientAddress;
	private final boolean connectsEnvironments;
	private final @NotNull Supplier<@Nullable String> unavailable;
	private final @NotNull InetAddress mirrorAddress;

	@Override
	public @NotNull String id() {
		return id;
	}

	@Override
	public boolean preservesClientAddress() {
		return preservesClientAddress;
	}

	@Override
	public boolean connectsEnvironments() {
		return connectsEnvironments;
	}

	@Override
	public @Nullable String unavailableReason() {
		return unavailable.get();
	}

	@Override
	public @NotNull ExecutionSession open(@NotNull ExecutionContext context) {
		var arguments = connection.mapper().createObjectNode()
				.put("provider", id)
				.put("priority", context.getProcessPriority().name());
		arguments.set("networkPolicy", connection.mapper().valueToTree(context.getNetworkPolicy()));

		return new RemoteExecutionSession(connection, connection.call("execution.open", arguments).asText(), mirrorAddress);
	}
}
