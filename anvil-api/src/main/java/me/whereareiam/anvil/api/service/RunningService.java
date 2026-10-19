package me.whereareiam.anvil.api.service;

import me.whereareiam.anvil.api.type.ServiceState;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * A scenario's service while it runs: where the test reaches it, and how a test makes it fail. Pausing it is how a
 * test cuts the scenario's processes off from it without losing what it holds, as in a network split; killing it is
 * how a test crashes it.
 *
 * <pre>{@code
 * RunningService redis = anvil.services().get("redis");
 * redis.pause();
 * // ... the processes cannot reach Redis
 * redis.resume();
 * ServiceCommandResult keys = redis.execute("redis-cli", "--scan");
 * }</pre>
 */
public interface RunningService {
	/**
	 * Returns the service's name.
	 */
	@NotNull String name();

	/**
	 * Returns what the service is doing.
	 */
	@NotNull ServiceState state();

	/**
	 * Returns where the test reaches one of the service's container ports.
	 *
	 * @param containerPort a port the service declared
	 * @return the address reachable from the test
	 * @throws IllegalArgumentException when the service did not declare the port
	 */
	@NotNull InetSocketAddress address(int containerPort);

	/**
	 * Pauses the container: it keeps its state but answers nobody until {@link #resume() resumed}.
	 *
	 * @throws IllegalStateException when the service is not running
	 */
	void pause();

	/**
	 * Resumes a paused container.
	 *
	 * @throws IllegalStateException when the service is not paused
	 */
	void resume();

	/**
	 * Kills the container right away, as a crash would.
	 */
	void kill();

	/**
	 * Starts the container again after it stopped, was killed or while it runs, and waits until it is ready. Its
	 * addresses stay the same; what it held is kept unless its image keeps nothing across restarts.
	 *
	 * @throws IllegalStateException when it does not become ready in time
	 */
	void restart();

	/**
	 * Runs a command inside the running container and waits for it to finish.
	 *
	 * @param command the command and its arguments, such as {@code redis-cli}, {@code PING}
	 * @return its exit code and output
	 * @throws IllegalStateException when the service is not running or the command does not finish in time
	 */
	@NotNull ServiceCommandResult execute(@NotNull String... command);

	/**
	 * Runs a command inside the running container and waits for it to finish at most for the given time.
	 *
	 * @param timeout how long to wait
	 * @param command the command and its arguments
	 * @return its exit code and output
	 * @throws IllegalStateException when the service is not running or the command does not finish in time
	 */
	@NotNull ServiceCommandResult execute(@NotNull Duration timeout, @NotNull String... command);
}
