package me.whereareiam.anvil.agent.client.transport.connection;

import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;

/**
 * Host-side provider for authenticated loopback JSON-lines agent connections.
 */
public final class JsonLineAgentConnectionProvider implements AgentConnectionProvider {
	@Override
	public @NotNull AgentClient connect(int port, @NotNull String token, @NotNull Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		Throwable latest = null;
		while (System.nanoTime() < deadline) {
			JsonLineAgentConnection connection = null;
			try {
				connection = new JsonLineAgentConnection(port, token);
				connection.request(AgentOperations.PING, Map.of());
				return new JsonAgentClient(connection);
			} catch (RuntimeException exception) {
				if (connection != null)
					connection.close();
				latest = exception;
				try {
					Thread.sleep(50);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new AgentException("Interrupted while connecting to platform agent", interrupted);
				}
			}
		}
		throw new AgentException("Platform agent on loopback:" + port + " did not become ready", latest);
	}
}
