package me.whereareiam.anvil.api.process.type;

import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.process.RunningProcess;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * A running {@link ContainerProcess}: where the test reaches its ports, and commands run inside it.
 *
 * <pre>{@code
 * RunningContainer redis = anvil.processes().container("redis");
 * InetSocketAddress address = redis.port(6379);
 * RunningContainer.CommandResult keys = redis.execute("redis-cli", "--scan");
 * }</pre>
 */
public interface RunningContainer extends RunningProcess {
	/**
	 * Returns where the test reaches one of the container's ports.
	 *
	 * @param containerPort a port the container declared
	 * @return the port's address on the host
	 * @throws IllegalArgumentException when the container did not declare the port
	 */
	@NotNull InetSocketAddress port(int containerPort);

	/**
	 * Runs a command inside the running container and waits at most thirty seconds for it to finish.
	 *
	 * @param command the command and its arguments, such as {@code redis-cli}, {@code PING}
	 * @return its exit code and output
	 * @throws IllegalStateException when the container is not running or the command does not finish in time
	 */
	default @NotNull CommandResult execute(@NotNull String... command) {
		return execute(Duration.ofSeconds(30), command);
	}

	/**
	 * Runs a command inside the running container and waits for it to finish at most for the given time.
	 *
	 * @param timeout how long to wait
	 * @param command the command and its arguments
	 * @return its exit code and output
	 * @throws IllegalStateException when the container is not running or the command does not finish in time
	 */
	@NotNull CommandResult execute(@NotNull Duration timeout, @NotNull String... command);

	/**
	 * What a command run inside a container returned.
	 *
	 * @param exitCode the command's exit code
	 * @param output   what it printed, standard output and standard error together
	 */
	record CommandResult(int exitCode, @NotNull String output) {
	}
}
