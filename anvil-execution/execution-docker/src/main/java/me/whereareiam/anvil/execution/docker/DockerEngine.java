package me.whereareiam.anvil.execution.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.execution.docker.image.DockerImageClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Owns the Docker Engine connection used by one execution session.
 */
public final class DockerEngine implements AutoCloseable {
    private final DockerClient client;
    private final DockerImageClient images;
    private final DockerContainerFactory containers;

    /**
     * Connects to the local Docker daemon using docker-java's default configuration.
     */
    public DockerEngine() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient transport = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(5))
                .build();

        client = DockerClientBuilder.getInstance(config).withDockerHttpClient(transport).build();
        images = new DockerImageClient(client);
        containers = new DockerContainerFactory(client);
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
