package me.whereareiam.anvil.agent.api.transport.connection;

import me.whereareiam.anvil.agent.api.transport.AgentClient;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Service-provider contract for the host-side transport used to reach platform agents.
 */
public interface AgentConnectionProvider {
	/**
	 * Connects to an agent, waiting for its runtime to become ready.
	 *
	 * @param port loopback port allocated to the agent
	 * @param token per-run authentication token
	 * @param timeout connection deadline
	 * @return typed authenticated agent client
	 */
	@NotNull AgentClient connect(int port, @NotNull String token, @NotNull Duration timeout);
}
