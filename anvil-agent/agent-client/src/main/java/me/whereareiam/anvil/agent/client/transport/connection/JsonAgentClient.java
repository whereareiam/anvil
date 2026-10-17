package me.whereareiam.anvil.agent.client.transport.connection;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * JSON-backed implementation of the typed platform-agent contract.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class JsonAgentClient implements AgentClient {
	private final @NotNull AgentConnection connection;

	@Override
	public boolean available() {
		return connection.available();
	}

	@Override
	public <T> @Nullable T request(@NotNull String operation, @Nullable Object arguments, @NotNull Class<T> responseType) {
		return connection.request(operation, arguments, responseType);
	}

	@Override
	public void close() {
		connection.close();
	}
}
