package me.whereareiam.anvil.agent.server.api.transport;

/**
 * Owns a running platform-agent endpoint and its accepted connections.
 */
public interface AgentServer extends AutoCloseable {
	/**
	 * Stops accepting requests and closes all connections owned by this endpoint.
	 */
	@Override
	void close();
}
