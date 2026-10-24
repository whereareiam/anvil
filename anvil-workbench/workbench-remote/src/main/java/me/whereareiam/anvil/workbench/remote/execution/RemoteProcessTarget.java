package me.whereareiam.anvil.workbench.remote.execution;

import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
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
	private final @NotNull String handle;
	private final @NotNull InetSocketAddress address;
	private final @NotNull InetSocketAddress peerAddress;
	private final @NotNull String bindAddress;
	private final @NotNull InetSocketAddress agentAddress;
	private final int agentPort;
	private final @NotNull String agentBindAddress;
	private final @NotNull Map<String, InetSocketAddress> listeners;
	private final @NotNull Map<String, InetSocketAddress> peerListeners;
	private final @NotNull PortMirror mirror;

	RemoteProcessTarget(
			@NotNull WorkbenchConnection connection,
			@NotNull String handle,
			@NotNull InetSocketAddress address,
			@NotNull InetSocketAddress peerAddress,
			@NotNull String bindAddress,
			@NotNull InetSocketAddress agentAddress,
			int agentPort,
			@NotNull String agentBindAddress,
			@NotNull Map<String, InetSocketAddress> listeners,
			@NotNull Map<String, InetSocketAddress> peerListeners,
			@NotNull InetAddress mirrorAddress
	) {
		this.connection = connection;
		this.handle = handle;
		this.address = address;
		this.peerAddress = peerAddress;
		this.bindAddress = bindAddress;
		this.agentAddress = agentAddress;
		this.agentPort = agentPort;
		this.agentBindAddress = agentBindAddress;
		this.listeners = Map.copyOf(listeners);
		this.peerListeners = Map.copyOf(peerListeners);
		this.mirror = new PortMirror(connection, mirrorAddress);
		try {
			mirror.mirror(address);
			mirror.mirror(agentAddress);
			listeners.values().forEach(mirror::mirror);
		} catch (RuntimeException failure) {
			close();
			throw failure;
		}
	}

	@Override
	public @NotNull InetSocketAddress address() {
		return address;
	}

	@Override
	public @NotNull InetSocketAddress peerAddress() {
		return peerAddress;
	}

	@Override
	public @NotNull String bindAddress() {
		return bindAddress;
	}

	@Override
	public @NotNull InetSocketAddress agentAddress() {
		return agentAddress;
	}

	@Override
	public int agentPort() {
		return agentPort;
	}

	@Override
	public @NotNull String agentBindAddress() {
		return agentBindAddress;
	}

	@Override
	public @NotNull Map<String, InetSocketAddress> listeners() {
		return listeners;
	}

	@Override
	public @NotNull Map<String, InetSocketAddress> peerListeners() {
		return peerListeners;
	}

	@Override
	public @NotNull ProcessExecution start(@NotNull ProcessLaunch launch) {
		var arguments = connection.mapper().createObjectNode().put("target", handle);
		arguments.set("launch", connection.mapper().valueToTree(launch));

		return RemoteProcessExecution.attach(connection, connection.call("target.start", arguments).asText());
	}

	@Override
	public void close() {
		try (mirror) {
			connection.call("target.close", connection.mapper().createObjectNode().put("handle", handle));
		}
	}
}
