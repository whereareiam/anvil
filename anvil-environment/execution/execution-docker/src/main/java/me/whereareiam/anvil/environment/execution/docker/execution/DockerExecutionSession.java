package me.whereareiam.anvil.environment.execution.docker.execution;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.api.type.network.NetworkServerAccess;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.execution.docker.DockerContainerFactory;
import me.whereareiam.anvil.environment.execution.docker.DockerEngine;
import me.whereareiam.anvil.environment.execution.docker.DockerNetwork;
import me.whereareiam.anvil.environment.execution.docker.image.DockerImageResolver;
import me.whereareiam.anvil.environment.execution.docker.process.DockerProcessTarget;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns a private container network and every prepared target, including partial startup.
 */
final class DockerExecutionSession implements ExecutionSession {
	private final ExecutionContext context;
	private final DockerEngine docker;
	private final DockerContainerFactory containers;
	private final DockerNetwork dockerNetwork;
	private final DockerImageResolver images;

	private final List<DockerProcessTarget> targets = new ArrayList<>();
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
		if (request.getJavaSelection().getSource() != null)
			throw new ProvisioningException("Docker execution does not support explicit host Java sources; configure a Docker image instead");

		String image = images.resolve(request);
		String alias = "p-" + UUID.nameUUIDFromBytes(request.getName().getBytes(StandardCharsets.UTF_8));
		synchronized (this) {
			if (closed) throw new IllegalStateException("Docker environment is closed");

			DockerProcessTarget target = new DockerProcessTarget(containers, dockerNetwork.id(), alias, image, request,
					new InetSocketAddress(context.getNetworkPolicy().getBindAddress(), port(context.getNetworkPolicy().getBindAddress())),
					new InetSocketAddress("127.0.0.1", port("127.0.0.1")));
			targets.add(target);
			return target;
		}
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
		for (DockerProcessTarget target : targets.reversed())
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
