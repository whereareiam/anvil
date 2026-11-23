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
			mirror.mirror(prepared.address());
			if (prepared.agentAddress() != null) mirror.mirror(prepared.agentAddress());
			prepared.listeners().values().forEach(mirror::mirror);
		} catch (RuntimeException failure) {
			close();
			throw failure;
		}
	}

	@Override
	public @NotNull InetSocketAddress address() {
		return prepared.address();
	}

	@Override
	public @NotNull InetSocketAddress peerAddress() {
		return prepared.peerAddress();
	}

	@Override
	public @NotNull String bindAddress() {
		return prepared.bindAddress();
	}

	@Override
	public @NotNull InetSocketAddress agentAddress() {
		if (prepared.agentAddress() == null) throw new UnsupportedOperationException(prepared.noAgent());
		return prepared.agentAddress();
	}

	@Override
	public int agentPort() {
		agentAddress();
		return prepared.agentPort();
	}

	@Override
	public @NotNull String agentBindAddress() {
		if (prepared.agentBindAddress() == null) throw new UnsupportedOperationException(prepared.noAgent());
		return prepared.agentBindAddress();
	}

	@Override
	public @NotNull Map<String, InetSocketAddress> listeners() {
		return prepared.listeners();
	}

	@Override
	public @NotNull Map<String, InetSocketAddress> peerListeners() {
		return prepared.peerListeners();
	}

	@Override
	public @NotNull ProcessExecution start(@NotNull ProcessLaunch launch) {
		return RemoteProcessExecution.attach(connection, connection.call(new StartProcess(prepared.handle(), launch)).handle());
	}

	@Override
	public void close() {
		try (mirror) {
			connection.call(new CloseTarget(prepared.handle()));
		}
	}
}
