package me.whereareiam.anvil.environment.service.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.service.ServiceContainer;
import me.whereareiam.anvil.environment.service.api.ServiceInstance;
import me.whereareiam.anvil.environment.service.api.ServiceSession;
import me.whereareiam.anvil.environment.service.api.model.ServiceContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Owns the Docker client and the containers of one scenario's services, with the host ports they publish.
 */
final class DockerServiceSession implements ServiceSession {
	private final ServiceContext context;
	private final DockerClient client;
	private final List<DockerService> services = new ArrayList<>();
	private final List<Integer> ports = new ArrayList<>();
	private boolean closed;

	DockerServiceSession(ServiceContext context) {
		this.context = context;

		DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		client = DockerClientImpl.getInstance(config, new ZerodepDockerHttpClient.Builder()
				.dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig())
				.connectionTimeout(Duration.ofSeconds(30))
				.responseTimeout(Duration.ofMinutes(5))
				.build());
	}

	@Override
	public synchronized @NotNull ServiceInstance start(@NotNull ServiceContainer declaration) {
		if (closed) throw new IllegalStateException("The services of this scenario are closed");

		ensureImage(declaration.getImage());
		Map<Integer, InetSocketAddress> published = publish(declaration);
		String id = create(declaration, published);
		DockerService service = new DockerService(client, id, declaration, published, context.getNetwork() != null,
				context.getStartup());
		services.add(service);
		if (context.getNetwork() != null) join(id, declaration.getName());

		service.start();
		return service;
	}

	private void ensureImage(String image) {
		try {
			client.inspectImageCmd(image).exec();
			return;
		} catch (NotFoundException missing) {
			if (context.isOffline())
				throw new ProvisioningException("Docker image " + image + " is not present and Anvil runs offline", missing);
		}

		try {
			if (!client.pullImageCmd(image).start().awaitCompletion(5, TimeUnit.MINUTES))
				throw new ProvisioningException("Timed out pulling Docker image " + image);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted pulling Docker image " + image, failure);
		} catch (RuntimeException failure) {
			throw new ProvisioningException("Could not pull Docker image " + image, failure);
		}
	}

	private Map<Integer, InetSocketAddress> publish(ServiceContainer declaration) {
		Map<Integer, InetSocketAddress> published = new LinkedHashMap<>();
		for (int port : declaration.getPorts()) {
			int host = context.getPorts().reserve(context.getBindAddress());
			ports.add(host);
			published.put(port, new InetSocketAddress(context.getBindAddress(), host));
		}

		return published;
	}

	private String create(ServiceContainer declaration, Map<Integer, InetSocketAddress> published) {
		Ports bindings = new Ports();
		published.forEach((port, host) -> bindings.bind(ExposedPort.tcp(port),
				Ports.Binding.bindIpAndPort(host.getHostString(), host.getPort())));

		try {
			var create = client.createContainerCmd(declaration.getImage())
					.withName("anvil-service-" + declaration.getName() + "-" + UUID.randomUUID().toString().substring(0, 8))
					.withLabels(Map.of("me.whereareiam.anvil", "true"))
					.withHostConfig(HostConfig.newHostConfig().withPortBindings(bindings).withAutoRemove(false))
					.withExposedPorts(published.keySet().stream().map(ExposedPort::tcp).toArray(ExposedPort[]::new))
					.withEnv(declaration.getEnvironment().entrySet().stream()
					.map(entry -> entry.getKey() + "=" + entry.getValue())
					.toList());
			if (!declaration.getCommand().isEmpty()) create.withCmd(declaration.getCommand());

			return create.exec().getId();
		} catch (RuntimeException failure) {
			throw new ProvisioningException("Could not create the container of service '" + declaration.getName() + "'", failure);
		}
	}

	// Published ports stay on the default bridge, which still publishes them when the processes' network is internal.
	private void join(String id, String name) {
		try {
			client.connectToNetworkCmd()
					.withNetworkId(context.getNetwork())
					.withContainerId(id)
					.withContainerNetwork(new ContainerNetwork().withAliases(name))
					.exec();
		} catch (RuntimeException failure) {
			throw new ProvisioningException("Could not connect service '" + name + "' to network " + context.getNetwork(), failure);
		}
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;

		RuntimeException failure = null;
		for (DockerService service : services.reversed())
			failure = attempt(service::remove, failure);
		failure = attempt(this::closeClient, failure);
		failure = attempt(this::releasePorts, failure);

		if (failure != null) throw failure;
	}

	private void closeClient() {
		try {
			client.close();
		} catch (IOException failure) {
			throw new ProvisioningException("Could not close the Docker client", failure);
		}
	}

	private void releasePorts() {
		ports.forEach(context.getPorts()::release);
		ports.clear();
	}

	private static RuntimeException attempt(Runnable action, RuntimeException failure) {
		try {
			action.run();
		} catch (RuntimeException cleanup) {
			if (failure == null) return cleanup;
			failure.addSuppressed(cleanup);
		}

		return failure;
	}
}
