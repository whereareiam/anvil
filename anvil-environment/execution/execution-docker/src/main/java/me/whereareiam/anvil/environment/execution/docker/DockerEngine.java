package me.whereareiam.anvil.environment.execution.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.execution.docker.image.DockerImageClient;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Owns the Docker Engine connection used by one execution session.
 */
public final class DockerEngine implements AutoCloseable {
	private static final Duration PING_TIMEOUT = Duration.ofSeconds(5);

	private final DockerClient client;
	private final DockerImageClient images;
	private final DockerContainerFactory containers;

	/**
	 * Connects to the local Docker daemon using docker-java's default configuration.
	 */
	public DockerEngine() {
		this(Duration.ofSeconds(30), Duration.ofMinutes(5));
	}

	private DockerEngine(Duration connectionTimeout, Duration responseTimeout) {
		DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		DockerHttpClient transport = new ZerodepDockerHttpClient.Builder()
				.dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig())
				.connectionTimeout(connectionTimeout)
				.responseTimeout(responseTimeout)
				.build();

		client = DockerClientImpl.getInstance(config, transport);
		images = new DockerImageClient(client);
		containers = new DockerContainerFactory(client);
	}

	/**
	 * Asks the daemon that docker-java's default configuration names whether it answers, waiting a few seconds at
	 * most.
	 *
	 * @return why the daemon cannot be used, or null when it answers
	 */
	public static @Nullable String unreachable() {
		try (DockerEngine docker = new DockerEngine(PING_TIMEOUT, PING_TIMEOUT)) {
			docker.client.pingCmd().exec();
			return null;
		} catch (RuntimeException failure) {
			String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
			return "the Docker daemon does not answer (" + detail + ")";
		}
	}

	public DockerImageClient images() {
		return images;
	}

	public DockerContainerFactory containers() {
		return containers;
	}

	public DockerNetwork createNetwork(String name, boolean internal) {
		return new DockerNetwork(client, client.createNetworkCmd()
				.withName(name)
				.withDriver("bridge")
				.withCheckDuplicate(true)
				.withInternal(internal)
				.withLabels(Map.of("me.whereareiam.anvil", "true"))
				.exec()
				.getId());
	}

	@Override
	public void close() {
		try {
			client.close();
		} catch (IOException failure) {
			throw new ProvisioningException("Could not close Docker client", failure);
		}
	}
}
