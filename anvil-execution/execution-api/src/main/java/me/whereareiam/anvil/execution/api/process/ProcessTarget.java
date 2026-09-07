package me.whereareiam.anvil.execution.api.process;

import me.whereareiam.anvil.execution.api.model.JavaCommand;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

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
	 * Starts one generation using the resolved JAR and fresh environment values.
	 *
	 * @param command Java arguments and the host-side JAR location
	 * @return owned execution, including streams and termination
	 */
	@NotNull ProcessExecution start(@NotNull JavaCommand command);

	/**
	 * Releases process-specific resources after its last generation has stopped.
	 */
	@Override
	void close();
}
