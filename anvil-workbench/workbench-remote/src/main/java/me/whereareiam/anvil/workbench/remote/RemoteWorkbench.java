package me.whereareiam.anvil.workbench.remote;

import me.whereareiam.anvil.environment.execution.api.BoundExecutionProvider;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.api.model.JavaMainLaunch;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import me.whereareiam.anvil.workbench.protocol.model.connection.Reverse;
import me.whereareiam.anvil.workbench.protocol.model.launch.Launch;
import me.whereareiam.anvil.workbench.protocol.model.message.ReverseConnection;
import me.whereareiam.anvil.workbench.protocol.model.workbench.Expose;
import me.whereareiam.anvil.workbench.protocol.model.workbench.Greeting;
import me.whereareiam.anvil.workbench.protocol.model.workbench.Hello;
import me.whereareiam.anvil.workbench.protocol.model.workbench.OfferedExecution;
import me.whereareiam.anvil.workbench.protocol.model.workbench.ReadFacts;
import me.whereareiam.anvil.workbench.remote.execution.RemoteExecutionProvider;
import me.whereareiam.anvil.workbench.remote.pairing.Pairing;
import me.whereareiam.anvil.workbench.remote.provisioning.RemoteArtifacts;
import me.whereareiam.anvil.workbench.remote.provisioning.RemoteJava;
import me.whereareiam.anvil.workbench.remote.provisioning.RemoteWorkspaces;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A workbench on another machine, paired with this engine: the engine plans and orchestrates here, and everything
 * the workbench holds and runs stays there. Addresses of its processes are mirrored on this machine's loopback,
 * files of this machine reach it through {@link #store(Path)}, and services on this machine's loopback reach its
 * processes through {@link #expose(int)}.
 */
public final class RemoteWorkbench implements Workbench {
	private final @NotNull WorkbenchConnection connection;
	private final @NotNull String name;
	private final @NotNull Path workDirectory;
	private final @NotNull Path cacheDirectory;
	private final @NotNull Map<String, BoundExecutionProvider> executions;
	private final @NotNull Uploads uploads;
	private final @NotNull RemoteArtifacts artifacts;
	private final @NotNull RemoteJava java;
	private final @NotNull RemoteWorkspaces workspaces;
	private final Set<Integer> exposed = ConcurrentHashMap.newKeySet();

	private RemoteWorkbench(@NotNull Pairing pairing, @NotNull InetAddress mirrorAddress) {
		this.connection = WorkbenchConnection.open(pairing, this::carryBack);
		try {
			Greeting greeting = connection.call(new Hello(WireProtocol.VERSION));
			name = pairing.getName();
			workDirectory = greeting.getWorkDirectory();
			cacheDirectory = greeting.getCacheDirectory();

			Map<String, BoundExecutionProvider> providers = new LinkedHashMap<>();
			for (OfferedExecution offered : greeting.getExecutions())
				providers.put(offered.getId(), new RemoteExecutionProvider(connection, offered,
						() -> facts().getUnavailableExecutions().get(offered.getId()), mirrorAddress));
			executions = Map.copyOf(providers);
		} catch (RuntimeException failure) {
			try {
				connection.close();
			} catch (IOException cleanup) {
				failure.addSuppressed(cleanup);
			}
			throw failure;
		}
		uploads = new Uploads(connection);
		artifacts = new RemoteArtifacts(connection);
		java = new RemoteJava(connection);
		workspaces = new RemoteWorkspaces(connection);
	}

	/**
	 * Connects to a paired workbench and checks that it speaks this engine's protocol.
	 *
	 * @param pairing the engine's pairing
	 * @return the connected workbench
	 * @throws WorkbenchException when it cannot be reached, refuses the engine or speaks another protocol
	 */
	public static @NotNull RemoteWorkbench connect(@NotNull Pairing pairing) {
		return new RemoteWorkbench(pairing, InetAddress.getLoopbackAddress());
	}

	/**
	 * Connects to a paired workbench whose processes' ports are mirrored at another loopback address, which an
	 * engine and a workbench on the same machine need, as both would otherwise take the same port.
	 *
	 * @param pairing the engine's pairing
	 * @param mirrorAddress loopback address to mirror ports at, such as {@code 127.0.0.2}
	 * @return the connected workbench
	 * @throws WorkbenchException when it cannot be reached, refuses the engine or speaks another protocol
	 */
	public static @NotNull RemoteWorkbench connect(@NotNull Pairing pairing, @NotNull InetAddress mirrorAddress) {
		return new RemoteWorkbench(pairing, mirrorAddress);
	}

	@Override
	public @NotNull String name() {
		return name;
	}

	@Override
	public @NotNull WorkbenchFacts facts() {
		return connection.call(new ReadFacts());
	}

	@Override
	public @NotNull Map<String, BoundExecutionProvider> executions() {
		return executions;
	}

	@Override
	public @NotNull Path workDirectory() {
		return workDirectory;
	}

	@Override
	public @NotNull Path cacheDirectory() {
		return cacheDirectory;
	}

	@Override
	public @NotNull Path store(@NotNull Path file) {
		return uploads.store(file);
	}

	@Override
	public void expose(int port) throws IOException {
		if (!exposed.add(port)) return;
		try {
			connection.request(new Expose(port));
		} catch (IOException | RuntimeException failure) {
			exposed.remove(port);
			throw failure;
		}
	}

	@Override
	public @NotNull Process launch(@NotNull JavaMainLaunch launch) throws IOException {
		return new RemoteProcess(connection, connection.request(new Launch(launch)).getHandle());
	}

	@Override
	public @NotNull ArtifactResolver artifacts() {
		return artifacts;
	}

	@Override
	public @NotNull JavaProvisioner java() {
		return java;
	}

	@Override
	public @NotNull WorkspaceProvisioner workspaces() {
		return workspaces;
	}

	/**
	 * Carries a connection a workbench process made to an exposed port back to the service on this machine.
	 */
	private void carryBack(ReverseConnection reverse) {
		Thread.ofVirtual().name("anvil-reverse").start(() -> {
			try {
				Socket local = new Socket();
				local.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), reverse.getPort()), 5000);
				Socket remote = connection.stream(token -> new Reverse(token, reverse.getId()));
				relay(local, remote);
				relay(remote, local);
			} catch (IOException unreachable) {
				// The service on this machine is gone; the workbench's connection ends with the stream.
			}
		});
	}

	private static void relay(Socket from, Socket to) {
		Thread.ofVirtual().name("anvil-reverse-relay").start(() -> {
			try (to) {
				from.getInputStream().transferTo(to.getOutputStream());
			} catch (IOException ignored) {
				// Either end went away.
			}
		});
	}

	@Override
	public void close() {
		try {
			connection.close();
		} catch (IOException ignored) {
			// The workbench releases everything the engine held when the connection ends.
		}
	}
}
