package me.whereareiam.anvil.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A process that runs a container image, such as a database or a message broker the scenario's servers and proxies
 * connect to. Any image works; Anvil knows nothing about what runs in it. It is a process like a server: it starts
 * before the scenario's servers and proxies, answers {@code anvil.processes()}, and can be stopped, killed, frozen and
 * restarted, where a restart starts a fresh container from the image.
 *
 * <pre>{@code
 * AnvilScenario.builder()
 *         .container(ContainerProcess.builder()
 *                 .name("redis")
 *                 .image("redis:7.2-alpine")
 *                 .port(6379)
 *                 .readyLog("Ready to accept connections")
 *                 .executionProviderId("docker")
 *                 .build())
 *         // ...
 * }</pre>
 *
 * The other processes reach it through {@code ${anvil.process.redis.host}} and {@code ${anvil.process.redis.port}};
 * see {@link ProcessAddresses}.
 */
@Value
@Builder(toBuilder = true)
public class ContainerProcess {
	/**
	 * The process's name: lowercase letters, digits and dashes, unique among the scenario's processes and remotes.
	 */
	@NotNull String name;

	/**
	 * The image to run, such as {@code redis:7.2-alpine}; pulled when the machine does not have it.
	 */
	@NotNull String image;

	/**
	 * The ports the container listens on, at least one. The first is its main port, named by
	 * {@code ${anvil.process.<name>.port}}; each is named by {@code ${anvil.process.<name>.port.<port>}}.
	 */
	@NotNull
	@Singular
	List<Integer> ports;

	/**
	 * Environment variables of the container, such as a database's password. Values may name other processes'
	 * addresses through their placeholders.
	 */
	@NotNull
	@Singular("environment")
	Map<String, String> environment;

	/**
	 * The command the container runs instead of its image's own; empty for the image's own.
	 */
	@NotNull
	@Singular("command")
	List<String> command;

	/**
	 * A regular expression the container prints once it is ready, such as {@code Ready to accept connections}.
	 * Without one, the container is ready once each of its ports reaches a listener.
	 */
	@Nullable String readyLog;

	/**
	 * The execution provider that runs the container, such as {@code docker}; null for the scenario's. Local
	 * execution runs no images, so a scenario whose servers run locally names one here.
	 */
	@Nullable String executionProviderId;

	/**
	 * The memory the container is expected to use, which Anvil counts when it starts processes at once.
	 */
	@Builder.Default
	int memoryMegabytes = 256;
}
