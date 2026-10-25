package me.whereareiam.anvil.workbench.remote.execution;

import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.workbench.protocol.model.execution.CloseTarget;
import me.whereareiam.anvil.workbench.protocol.model.execution.PreparedTarget;
import me.whereareiam.anvil.workbench.protocol.model.execution.StartProcess;
import me.whereareiam.anvil.workbench.remote.PortMirror;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * A prepared process on a workbench on another machine. Its loopback addresses are mirrored on the engine's
 * loopback as long as the target lives, so the engine reaches the process at the addresses the workbench reported.
 */
final class RemoteProcessTarget implements ProcessTarget {
	private final @NotNull WorkbenchConnection connection;
	private final @NotNull PreparedTarget prepared;
	private final @NotNull PortMirror mirror;

	RemoteProcessTarget(@NotNull WorkbenchConnection connection, @NotNull PreparedTarget prepared, @NotNull InetAddress mirrorAddress) {
		this.connection = connection;
		this.prepared = prepared;
		this.mirror = new PortMirror(connection, mirrorAddress);
		try {
			mirror.mirror(prepared.getAddress());
			if (prepared.getAgentAddress() != null) mirror.mirror(prepared.getAgentAddress());
			prepared.getListeners().values().forEach(mirror::mirror);
		} catch (RuntimeException failure) {
			close();
			throw failure;
		}
	}

	@Override
	public @NotNull InetSocketAddress address() {
		return prepared.getAddress();
	}

	@Override
	public @NotNull InetSocketAddress peerAddress() {
		return prepared.getPeerAddress();
	}

	@Override
	public @NotNull String bindAddress() {
		return prepared.getBindAddress();
	}

	@Override
	public @NotNull InetSocketAddress agentAddress() {
		if (prepared.getAgentAddress() == null) throw new UnsupportedOperationException(prepared.getNoAgent());
		return prepared.getAgentAddress();
	}

	@Override
	public int agentPort() {
		agentAddress();
		return prepared.getAgentPort();
	}

	@Override
	public @NotNull String agentBindAddress() {
		if (prepared.getAgentBindAddress() == null) throw new UnsupportedOperationException(prepared.getNoAgent());
		return prepared.getAgentBindAddress();
	}

	@Override
	public @NotNull Map<String, InetSocketAddress> listeners() {
		return prepared.getListeners();
	}

	@Override
	public @NotNull Map<String, InetSocketAddress> peerListeners() {
		return prepared.getPeerListeners();
	}

	@Override
	public @NotNull ProcessExecution start(@NotNull ProcessLaunch launch) {
		return RemoteProcessExecution.attach(connection, connection.call(new StartProcess(prepared.getHandle(), launch)).getHandle());
	}

	@Override
	public void close() {
		try (mirror) {
			connection.call(new CloseTarget(prepared.getHandle()));
		}
	}
}
