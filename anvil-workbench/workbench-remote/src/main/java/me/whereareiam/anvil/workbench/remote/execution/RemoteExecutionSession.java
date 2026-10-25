package me.whereareiam.anvil.workbench.remote.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.workbench.protocol.model.execution.CloseSession;
import me.whereareiam.anvil.workbench.protocol.model.execution.PrepareTarget;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;

/**
 * An execution session on a workbench on another machine.
 */
@RequiredArgsConstructor
final class RemoteExecutionSession implements ExecutionSession {
	private final @NotNull WorkbenchConnection connection;
	private final @NotNull String handle;
	private final @NotNull InetAddress mirrorAddress;

	@Override
	public @NotNull ProcessTarget prepare(@NotNull ProcessRequest request) {
		return new RemoteProcessTarget(connection, connection.call(new PrepareTarget(handle, request)), mirrorAddress);
	}

	@Override
	public void close() {
		connection.call(new CloseSession(handle));
	}
}
