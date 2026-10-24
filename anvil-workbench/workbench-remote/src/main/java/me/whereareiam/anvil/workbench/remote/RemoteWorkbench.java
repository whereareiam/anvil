package me.whereareiam.anvil.workbench.remote;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.type.ProcessPriority;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.PortReservations;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.api.model.JavaMainLaunch;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
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
	private final @NotNull ProcessPriority priority;
	private final @NotNull String name;
	private final @NotNull Path workDirectory;
	private final @NotNull Path cacheDirectory;
	private final @NotNull Map<String, ExecutionProvider> executions;
	private final @NotNull Uploads uploads;
	private final @NotNull RemoteArtifacts artifacts;
	private final @NotNull RemoteJava java;
	private final @NotNull RemoteWorkspaces workspaces;
	private final Set<Integer> exposed = ConcurrentHashMap.newKeySet();

	private RemoteWorkbench(@NotNull Pairing pairing, @NotNull ProcessPriority priority, @NotNull InetAddress mirrorAddress) {
		this.priority = priority;
		this.connection = WorkbenchConnection.open(pairing, this::event);
		try {
			JsonNode hello = connection.call("hello", connection.mapper().createObjectNode().put("version", WireProtocol.VERSION));
			name = pairing.getName();
			workDirectory = Path.of(hello.path("workDirectory").asText());
			cacheDirectory = Path.of(hello.path("cacheDirectory").asText());

			Map<String, ExecutionProvider> providers = new LinkedHashMap<>();
			connection.call("executions", connection.mapper().createObjectNode()).forEach(provider -> {
				String id = provider.path("id").asText();
				providers.put(id, new RemoteExecutionProvider(connection, id, provider.path("preservesClientAddress").asBoolean(),
						provider.path("connectsEnvironments").asBoolean(), () -> facts().getUnavailableExecutions().get(id), mirrorAddress));
			});
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
	 * @param priority priority of the processes this engine starts there
	 * @return the connected workbench
	 * @throws WorkbenchException when it cannot be reached, refuses the engine or speaks another protocol
	 */
	public static @NotNull RemoteWorkbench connect(@NotNull Pairing pairing, @NotNull ProcessPriority priority) {
		return new RemoteWorkbench(pairing, priority, InetAddress.getLoopbackAddress());
	}

	/**
	 * Connects to a paired workbench whose processes' ports are mirrored at another loopback address, which an
	 * engine and a workbench on the same machine need, as both would otherwise take the same port.
	 *
	 * @param pairing the engine's pairing
	 * @param priority priority of the processes this engine starts there
	 * @param mirrorAddress loopback address to mirror ports at, such as {@code 127.0.0.2}
	 * @return the connected workbench
	 * @throws WorkbenchException when it cannot be reached, refuses the engine or speaks another protocol
	 */
	public static @NotNull RemoteWorkbench connect(@NotNull Pairing pairing, @NotNull ProcessPriority priority, @NotNull InetAddress mirrorAddress) {
		return new RemoteWorkbench(pairing, priority, mirrorAddress);
	}

	@Override
	public @NotNull String name() {
		return name;
	}

	@Override
	public @NotNull WorkbenchFacts facts() {
		return connection.mapper().convertValue(connection.call("facts", connection.mapper().createObjectNode()), WorkbenchFacts.class);
	}

	@Override
	public @NotNull Map<String, ExecutionProvider> executions() {
		return executions;
	}

	/**
	 * Returns a context carrying the scenario's network policy and the engine's priority to the workbench; the
	 * ports, image locks and Java runtimes its sessions use are the workbench's own.
	 */
	@Override
	public @NotNull ExecutionContext context(@NotNull NetworkPolicy networkPolicy) {
		return ExecutionContext.builder()
				.cacheDirectory(cacheDirectory)
				.localRuntime((request, source) -> {
					throw new IllegalStateException("Java runtimes of workbench '" + name + "' resolve on the workbench");
				})
				.runtimeValidator((properties, request) -> {
					throw new IllegalStateException("Images of workbench '" + name + "' are validated on the workbench");
				})
				.imageLocks(image -> {
					throw new IllegalStateException("Images of workbench '" + name + "' are locked on the workbench");
				})
				.ports(new PortReservations() {
					@Override
					public int reserve(@NotNull String bindAddress) {
						throw new IllegalStateException("Ports of workbench '" + name + "' are reserved on the workbench");
					}

					@Override
					public void release(int port) {
						throw new IllegalStateException("Ports of workbench '" + name + "' are released on the workbench");
					}
				})
				.processPriority(priority)
				.networkPolicy(networkPolicy)
				.build();
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
			connection.request("expose", connection.mapper().createObjectNode().put("port", port));
		} catch (IOException | RuntimeException failure) {
			exposed.remove(port);
			throw failure;
		}
	}

	@Override
	public @NotNull Process launch(@NotNull JavaMainLaunch launch) throws IOException {
		var arguments = connection.mapper().createObjectNode();
		arguments.set("launch", connection.mapper().valueToTree(launch));
		return new RemoteProcess(connection, connection.request("launch", arguments).asText());
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
	private void event(JsonNode event) {
		if (!event.path("event").asText().equals("reverse")) return;

		Thread.ofVirtual().name("anvil-reverse").start(() -> {
			try {
				Socket local = new Socket();
				local.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), event.path("port").asInt()), 5000);
				Socket remote = connection.stream(WireProtocol.REVERSE, connection.mapper().createObjectNode().put("id", event.path("id").asText()));
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
