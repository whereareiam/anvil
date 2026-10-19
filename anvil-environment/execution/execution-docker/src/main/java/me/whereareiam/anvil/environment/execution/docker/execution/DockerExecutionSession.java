package me.whereareiam.anvil.environment.execution.docker.execution;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.api.type.network.NetworkServerAccess;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.process.ContainerImage;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.execution.docker.DockerContainerFactory;
import me.whereareiam.anvil.environment.execution.docker.DockerEngine;
import me.whereareiam.anvil.environment.execution.docker.DockerNetwork;
import me.whereareiam.anvil.environment.execution.docker.image.DockerImageResolver;
import me.whereareiam.anvil.environment.execution.docker.process.DockerImageTarget;
import me.whereareiam.anvil.environment.execution.docker.process.DockerProcessTarget;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns a private container network and every prepared target, including partial startup.
 */
final class DockerExecutionSession implements ExecutionSession {
	/** Listeners bind from here inside their container, after the game port 25565 and the agent's 25566. */
	private static final int FIRST_LISTENER_PORT = 25570;

	private final ExecutionContext context;
	private final DockerEngine docker;
	private final DockerContainerFactory containers;
	private final DockerNetwork dockerNetwork;
	private final DockerImageResolver images;

	private final List<ProcessTarget> targets = new ArrayList<>();
	private final Set<Integer> ports = new HashSet<>();
	private boolean closed;

	DockerExecutionSession(ExecutionContext context, DockerEngine docker, DockerExecutionSettings settings) {
		this.context = context;
		this.docker = docker;
		this.containers = docker.containers();
		this.images = new DockerImageResolver(context, docker.images(), settings);

		String host = System.getenv("DOCKER_HOST");
		if (host == null) host = "unix://local";
		if (!host.startsWith("unix://") && !host.startsWith("npipe://"))
			throw new ProvisioningException("Docker execution requires a local daemon with access to the scenario workspace");

		String network = "anvil-" + UUID.randomUUID();
		dockerNetwork = docker.createNetwork(network, context.getNetworkPolicy().getNetworkServerAccess()
				== NetworkServerAccess.PROXY_ONLY
				&& context.getNetworkPolicy().getBackendNetworkExposure()
				== NetworkExposure.PRIVATE);
	}

	@Override
	public @NotNull ProcessTarget prepare(@NotNull ProcessRequest request) {
		if (request.getImage() != null) return prepareImage(request, request.getImage());
		if (request.getJavaSelection() == null)
			throw new ProvisioningException("Process '" + request.getName() + "' names neither Java nor an image");
		if (request.getJavaSelection().getSource() != null)
			throw new ProvisioningException("Docker execution does not support explicit host Java sources; configure a Docker image instead");

		String image = images.resolve(request);
		String alias = alias(request);
		synchronized (this) {
			if (closed) throw new IllegalStateException("Docker environment is closed");

			String bindAddress = context.getNetworkPolicy().getBindAddress();
			Map<String, InetSocketAddress> listeners = new LinkedHashMap<>();
			Map<String, Integer> listenerPorts = new LinkedHashMap<>();
			for (String listener : request.getListeners()) {
				listeners.put(listener, new InetSocketAddress(bindAddress, port(bindAddress)));
				listenerPorts.put(listener, FIRST_LISTENER_PORT + listenerPorts.size());
			}

			DockerProcessTarget target = new DockerProcessTarget(containers, dockerNetwork.id(), alias, image, request,
					new InetSocketAddress(bindAddress, port(bindAddress)),
					new InetSocketAddress("127.0.0.1", port("127.0.0.1")),
					Map.copyOf(listeners),
					Map.copyOf(listenerPorts));
			targets.add(target);
			return target;
		}
	}

	private ProcessTarget prepareImage(ProcessRequest request, ContainerImage image) {
		images.ensure(image.getImage());
		synchronized (this) {
			if (closed) throw new IllegalStateException("Docker environment is closed");

			String bindAddress = context.getNetworkPolicy().getBindAddress();
			Map<Integer, InetSocketAddress> published = new LinkedHashMap<>();
			image.getPorts().forEach(port -> published.put(port, new InetSocketAddress(bindAddress, port(bindAddress))));

			DockerImageTarget target = new DockerImageTarget(containers, dockerNetwork.id(), alias(request), request,
					Collections.unmodifiableMap(published));
			targets.add(target);
			return target;
		}
	}

	private static String alias(ProcessRequest request) {
		return "p-" + UUID.nameUUIDFromBytes(request.getName().getBytes(StandardCharsets.UTF_8));
	}

	private int port(String address) {
		int port = context.getPorts().reserve(address);
		ports.add(port);

		return port;
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;

		RuntimeException failure = null;
		for (ProcessTarget target : targets.reversed())
			failure = close(target::close, failure);

		failure = close(dockerNetwork::close, failure);
		failure = close(docker::close, failure);
		failure = close(this::releasePorts, failure);

		if (failure != null) throw failure;
	}

	private void releasePorts() {
		ports.forEach(context.getPorts()::release);
		ports.clear();
	}

	private static RuntimeException close(Runnable action, RuntimeException failure) {
		try {
			action.run();
		} catch (RuntimeException cleanup) {
			if (failure == null) return cleanup;
			failure.addSuppressed(cleanup);
		}

		return failure;
	}
}
