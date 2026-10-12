package me.whereareiam.anvil.environment.execution.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.type.network.ListenerTransport;
import me.whereareiam.anvil.environment.execution.docker.container.ContainerAttachment;
import me.whereareiam.anvil.environment.execution.docker.model.Container;
import me.whereareiam.anvil.environment.execution.docker.model.ContainerConfiguration;
import me.whereareiam.anvil.environment.execution.docker.model.PortBindings;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Creates container handles from Anvil-owned immutable container specifications.
 */
@RequiredArgsConstructor
public final class DockerContainerFactory {
	/**
	 * Port the game listener binds inside every container, whichever host port publishes it. Other containers on
	 * the scenario network reach the game listener through the container's alias and this port.
	 */
	public static final int GAME_PORT = 25565;

	/**
	 * Port the platform agent binds inside every container, whichever host port publishes it.
	 */
	public static final int AGENT_PORT = 25566;

	private final DockerClient client;

	/**
	 * Creates a stopped container and returns its lifecycle handle.
	 */
	public Container create(ContainerConfiguration configuration) {
		Exposure exposure = exposure(configuration.getPorts());

		HostConfig host = HostConfig.newHostConfig()
				.withNetworkMode(configuration.getNetwork())
				.withPortBindings(exposure.bindings())
				.withBinds(new Bind(configuration.getWorkspace().toAbsolutePath().toString(), new Volume("/workspace")),
						new Bind(configuration.getJar().toAbsolutePath().toString(), new Volume("/anvil/application.jar")))
				.withAutoRemove(false);

		String id = client.createContainerCmd(configuration.getImage())
				.withName(configuration.getName())
				.withHostConfig(host)
				.withAliases(configuration.getAlias())
				.withExposedPorts(exposure.ports().toArray(ExposedPort[]::new))
				.withWorkingDir("/workspace")
				.withEntrypoint("java")
				.withEnv(configuration.getEnvironment().entrySet().stream()
						.map(entry -> entry.getKey() + "=" + entry.getValue())
						.toList())
				.withCmd(configuration.getCommand())
				.exec()
				.getId();

		return new Container(client, id);
	}

	/**
	 * Exposes the game port with its transport and the agent port over TCP, publishing each endpoint the
	 * bindings name on its host address.
	 */
	static Exposure exposure(PortBindings ports) {
		ExposedPort game = ports.getGameTransport() == ListenerTransport.UDP
				? ExposedPort.udp(GAME_PORT)
				: ExposedPort.tcp(GAME_PORT);
		Exposure exposure = new Exposure(new Ports(), new ArrayList<>());
		if (ports.getGame() != null) exposure.publish(game, ports.getGame());
		if (ports.getAgent() != null) exposure.publish(ExposedPort.tcp(AGENT_PORT), ports.getAgent());

		return exposure;
	}

	/** Attaches process streams to a container and starts its exit watcher. */
	public ContainerAttachment attach(Container container) {
		ContainerAttachment attachment = null;
		try {
			PipedInputStream output = new PipedInputStream(64 * 1024);
			PipedOutputStream outputWriter = new PipedOutputStream(output);
			PipedInputStream input = new PipedInputStream(8 * 1024);
			PipedOutputStream inputWriter = new PipedOutputStream(input);

			ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
				@Override
				public void onNext(Frame frame) {
					try {
						outputWriter.write(frame.getPayload());
						outputWriter.flush();
					} catch (IOException failure) {
						throw new ProvisioningException("Could not forward Docker output", failure);
					}
				}
			};

			attachment = new ContainerAttachment(output, outputWriter, input, inputWriter, callback);
			client.attachContainerCmd(container.id())
					.withStdOut(true)
					.withStdErr(true)
					.withStdIn(input)
					.withFollowStream(true)
					.exec(callback);

			container.start();
			ContainerAttachment attached = attachment;
			CompletableFuture.runAsync(() -> {
				try {
					container.awaitExit();
					attached.exited().complete(null);
				} catch (RuntimeException failure) {
					attached.exited().completeExceptionally(failure);
				} finally {
					attached.close();
				}
			});

			return attached;
		} catch (IOException | RuntimeException failure) {
			if (attachment != null) attachment.close();
			throw new ProvisioningException("Could not attach Docker container", failure);
		}
	}

	/**
	 * Container ports to expose and the host endpoints that publish them.
	 */
	record Exposure(Ports bindings, List<ExposedPort> ports) {
		private void publish(ExposedPort port, InetSocketAddress host) {
			bindings.bind(port, Ports.Binding.bindIpAndPort(host.getHostString(), host.getPort()));
			ports.add(port);
		}
	}
}
