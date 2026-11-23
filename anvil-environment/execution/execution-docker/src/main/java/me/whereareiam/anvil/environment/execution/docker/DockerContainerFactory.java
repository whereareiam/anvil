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
import me.whereareiam.anvil.environment.execution.docker.container.ContainerAttachment;
import me.whereareiam.anvil.environment.execution.docker.model.Container;
import me.whereareiam.anvil.environment.execution.docker.model.ContainerConfiguration;
import me.whereareiam.anvil.environment.execution.docker.model.ImageContainerConfiguration;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Creates container handles from Anvil-owned immutable container specifications.
 */
@RequiredArgsConstructor
public final class DockerContainerFactory {
	private static final Duration ATTACH_TIMEOUT = Duration.ofSeconds(30);

	private final DockerClient client;

	/**
	 * Creates a stopped container and returns its lifecycle handle.
	 */
	public Container create(ContainerConfiguration configuration) {
		ExposedPort game = ExposedPort.tcp(25565);
		ExposedPort agent = ExposedPort.tcp(25566);
		Ports bindings = new Ports();
		List<ExposedPort> exposed = new ArrayList<>();

		if (configuration.ports().game() != null) {
			bindings.bind(game, Ports.Binding.bindIpAndPort(configuration.ports().game().getHostString(),
					configuration.ports().game().getPort()));
			exposed.add(game);
		}
		if (configuration.ports().agent() != null) {
			bindings.bind(agent, Ports.Binding.bindIpAndPort(configuration.ports().agent().getHostString(),
					configuration.ports().agent().getPort()));
			exposed.add(agent);
		}

		configuration.ports().listeners().forEach((port, published) -> {
			ExposedPort listener = ExposedPort.tcp(port);
			bindings.bind(listener, Ports.Binding.bindIpAndPort(published.getHostString(), published.getPort()));
			exposed.add(listener);
		});

		HostConfig host = HostConfig.newHostConfig()
				.withNetworkMode(configuration.network())
				.withPortBindings(bindings)
				.withBinds(new Bind(configuration.workspace().toAbsolutePath().toString(), new Volume("/workspace")),
						new Bind(configuration.jar().toAbsolutePath().toString(), new Volume("/anvil/application.jar")))
				.withAutoRemove(false);
		if (configuration.cpuShares() != null) host.withCpuShares(configuration.cpuShares());

		String id = client.createContainerCmd(configuration.image())
				.withName(configuration.name())
				.withHostConfig(host)
				.withAliases(configuration.alias())
				.withExposedPorts(exposed.toArray(ExposedPort[]::new))
				.withWorkingDir("/workspace")
				.withEntrypoint("java")
				.withEnv(configuration.environment().entrySet().stream()
						.map(entry -> entry.getKey() + "=" + entry.getValue())
						.toList())
				.withCmd(configuration.command())
				.exec()
				.getId();

		return new Container(client, id);
	}

	/**
	 * Creates a stopped container that runs its own image, with stdin open for console commands.
	 */
	public Container createImage(ImageContainerConfiguration configuration) {
		Ports bindings = new Ports();
		List<ExposedPort> exposed = new ArrayList<>();
		configuration.ports().forEach((port, published) -> {
			ExposedPort exposedPort = ExposedPort.tcp(port);
			bindings.bind(exposedPort, Ports.Binding.bindIpAndPort(published.getHostString(), published.getPort()));
			exposed.add(exposedPort);
		});

		HostConfig host = HostConfig.newHostConfig()
				.withNetworkMode(configuration.network())
				.withPortBindings(bindings)
				.withAutoRemove(false);
		if (configuration.cpuShares() != null) host.withCpuShares(configuration.cpuShares());

		var create = client.createContainerCmd(configuration.image())
				.withName(configuration.name())
				.withLabels(Map.of("me.whereareiam.anvil", "true"))
				.withHostConfig(host)
				.withAliases(configuration.alias())
				.withExposedPorts(exposed.toArray(ExposedPort[]::new))
				.withStdinOpen(true)
				.withEnv(configuration.environment().entrySet().stream()
						.map(entry -> entry.getKey() + "=" + entry.getValue())
						.toList());
		if (!configuration.command().isEmpty()) create.withCmd(configuration.command());

		return new Container(client, create.exec().getId());
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
			// The attach is asynchronous. A container that prints its ready line at once, such as Redis, would print
			// it before the stream is connected, and the line would be lost. So the stream also replays what the
			// container printed since it started, and the container starts only once the stream is connected.
			client.attachContainerCmd(container.id())
					.withStdOut(true)
					.withStdErr(true)
					.withStdIn(input)
					.withLogs(true)
					.withFollowStream(true)
					.exec(callback);
			if (!callback.awaitStarted(ATTACH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
				throw new ProvisioningException("Docker did not connect to the output of container " + container.id()
						+ " within " + ATTACH_TIMEOUT);

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
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			if (attachment != null) attachment.close();
			throw new ProvisioningException("Interrupted while attaching to a Docker container", failure);
		} catch (IOException | RuntimeException failure) {
			if (attachment != null) attachment.close();
			throw new ProvisioningException("Could not attach Docker container", failure);
		}
	}
}
