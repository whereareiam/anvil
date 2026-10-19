package me.whereareiam.anvil.environment.execution.api.process;

import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Prepared process location with stable addresses across JVM replacements.
 */
public interface ProcessTarget extends AutoCloseable {
	/**
	 * Returns the listener address used by host-side players.
	 *
	 * @return externally reachable game endpoint
	 */
	@NotNull InetSocketAddress address();

	/**
	 * Returns the game endpoint used by peer processes in this scenario.
	 *
	 * @return peer-visible game endpoint
	 */
	@NotNull InetSocketAddress peerAddress();

	/**
	 * Returns the address the platform should bind inside its execution environment.
	 *
	 * @return process-local bind address
	 */
	@NotNull String bindAddress();

	/**
	 * Returns the host-side endpoint for the authenticated platform agent.
	 *
	 * @return reachable agent endpoint
	 */
	@NotNull InetSocketAddress agentAddress();

	/**
	 * Returns the port the agent should bind inside its process.
	 *
	 * @return process-local agent port
	 */
	int agentPort();

	/**
	 * Returns the address the agent should bind inside its process.
	 *
	 * @return process-local agent bind address
	 */
	@NotNull String agentBindAddress();

	/**
	 * Returns where host-side clients, such as the test, reach the process's listeners.
	 *
	 * @return host addresses by listener name
	 */
	default @NotNull Map<String, InetSocketAddress> listeners() {
		return Map.of();
	}

	/**
	 * Returns where peer processes reach the process's listeners: at the host of {@link #peerAddress()}, each on a
	 * port of its own. The process binds each listener on the port of this address, at its {@link #bindAddress()}.
	 *
	 * @return peer-visible addresses by listener name
	 */
	default @NotNull Map<String, InetSocketAddress> peerListeners() {
		return Map.of();
	}

	/**
	 * Starts one generation with fresh runtime values: a Java application from its JAR, or the target's container
	 * image.
	 *
	 * @param launch the Java command, or the image launch of a container process
	 * @return owned execution, including streams and termination
	 * @throws IllegalArgumentException when the launch does not fit the target, such as an image launch for a Java
	 *                                  application
	 */
	@NotNull ProcessExecution start(@NotNull ProcessLaunch launch);

	/**
	 * Releases process-specific resources after its last generation has stopped.
	 */
	@Override
	void close();
}
