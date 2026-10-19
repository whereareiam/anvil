package me.whereareiam.anvil.api.model.service;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A container a scenario runs next to its servers and proxies, such as a database or a message broker they connect to.
 * Anvil starts it before the scenario's processes, stops it after them, and tells each process where to reach it.
 * Any image works; Anvil knows nothing about what runs in it.
 *
 * <pre>{@code
 * AnvilScenario.builder()
 *         .service(ServiceContainer.builder()
 *                 .name("redis")
 *                 .image("redis:7.2-alpine")
 *                 .port(6379)
 *                 .build())
 *         .server(MinecraftServer.builder()
 *                 .name("lobby")
 *                 // ...
 *                 .build())
 *         // ...
 * }</pre>
 *
 * A process reaches the service through {@code ${anvil.service.redis.host}} and {@code ${anvil.service.redis.port}} in
 * its text assets and JVM arguments; see {@link ServiceAddresses}.
 */
@Value
@Builder(toBuilder = true)
public class ServiceContainer {
	/**
	 * The service's name: lowercase letters, digits and dashes, unique among the scenario's processes, remotes and
	 * services. Processes inside a container network reach the service under this host name, and its address
	 * placeholders name it.
	 */
	@NotNull String name;

	/**
	 * The image to run, such as {@code redis:7.2-alpine}; pulled when the machine does not have it.
	 */
	@NotNull String image;

	/**
	 * The ports the service listens on inside its container. Each is reachable from the scenario's processes and from
	 * the test, at the addresses Anvil hands them.
	 */
	@NotNull
	@Singular
	List<Integer> ports;

	/**
	 * Environment variables of the container, such as a database's password.
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
	 * A regular expression the container prints once it is ready, such as {@code Ready to accept connections}. Without
	 * one, the service is ready once each of its ports accepts connections.
	 */
	@Nullable String readyLog;
}
