package me.whereareiam.anvil.environment.execution.docker.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.model.ImageLaunch;
import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.execution.docker.DockerContainerFactory;
import me.whereareiam.anvil.environment.execution.docker.model.Container;
import me.whereareiam.anvil.environment.execution.docker.model.ImageContainerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns successive containers of a container process: each generation starts a fresh container from the image, on the
 * scenario's network under the process's alias, with its ports published on the same host addresses.
 */
@RequiredArgsConstructor
public final class DockerImageTarget implements ProcessTarget {
	private final DockerContainerFactory docker;

	private final String network;
	private final String alias;
	private final ProcessRequest request;
	/**
	 * Host addresses of the container's ports, by container port, in their declared order.
	 */
	private final Map<Integer, InetSocketAddress> published;
	/**
	 * The containers' CPU weight, or null for Docker's default.
	 */
	private final @Nullable Integer cpuShares;

	private @Nullable Container container;

	@Override
	public synchronized @NotNull ProcessExecution start(@NotNull ProcessLaunch launch) {
		if (!(launch instanceof ImageLaunch image))
			throw new IllegalArgumentException("Container '" + request.getName() + "' starts its image, not a Java application");

		close();
		Container created = docker.createImage(ImageContainerConfiguration.builder()
				.name("anvil-container-" + UUID.randomUUID())
				.network(network)
				.alias(alias)
				.image(Objects.requireNonNull(request.getImage()).getImage())
				.ports(published)
				.environment(image.getEnvironment())
				.command(image.getCommand())
				.cpuShares(cpuShares)
				.build());

		container = created;
		return new DockerProcess(docker, created);
	}

	@Override
	public @NotNull InetSocketAddress address() {
		return published.values().iterator().next();
	}

	@Override
	public @NotNull InetSocketAddress peerAddress() {
		return InetSocketAddress.createUnresolved(alias, published.keySet().iterator().next());
	}

	@Override
	public @NotNull String bindAddress() {
		return "0.0.0.0";
	}

	@Override
	public @NotNull InetSocketAddress agentAddress() {
		throw new UnsupportedOperationException("Container '" + request.getName() + "' runs no Anvil agent");
	}

	@Override
	public int agentPort() {
		throw new UnsupportedOperationException("Container '" + request.getName() + "' runs no Anvil agent");
	}

	@Override
	public @NotNull String agentBindAddress() {
		throw new UnsupportedOperationException("Container '" + request.getName() + "' runs no Anvil agent");
	}

	@Override
	public @NotNull Map<String, InetSocketAddress> listeners() {
		Map<String, InetSocketAddress> ports = new LinkedHashMap<>();
		published.forEach((port, address) -> ports.put(Integer.toString(port), address));

		return ports;
	}

	@Override
	public @NotNull Map<String, InetSocketAddress> peerListeners() {
		Map<String, InetSocketAddress> ports = new LinkedHashMap<>();
		published.keySet().forEach(port -> ports.put(Integer.toString(port), InetSocketAddress.createUnresolved(alias, port)));

		return ports;
	}

	@Override
	public synchronized void close() {
		if (container == null) return;
		container.remove();
		container = null;
	}
}
