package me.whereareiam.anvil.agent.client.api;

import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;

/**
 * Typed host-side contract for a running platform agent.
 */
public interface AgentClient extends AgentConnection {
	/**
	 * Closes the platform-agent connection.
	 */
	@Override
	void close();
}
