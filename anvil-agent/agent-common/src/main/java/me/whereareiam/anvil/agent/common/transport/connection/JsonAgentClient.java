package me.whereareiam.anvil.agent.common.transport.connection;

import me.whereareiam.anvil.agent.api.transport.connection.AgentConnection;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.common.transport.operation.CommandOperation;
import me.whereareiam.anvil.agent.common.transport.operation.IdentityOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * JSON-backed implementation of the typed platform-agent contract.
 */
final class JsonAgentClient implements AgentClient {
	private static final IdentityOperation IDENTITY = new IdentityOperation();
	private static final CommandOperation COMMAND = new CommandOperation();
	private final AgentConnection connection;

	JsonAgentClient(@NotNull AgentConnection connection) {
		this.connection = connection;
	}

	@Override
	public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
		return IDENTITY.request(connection, username);
	}

	@Override
	public boolean executeCommand(@NotNull String command) {
		return COMMAND.request(connection, command);
	}

	@Override
	public <T> @Nullable T request(@NotNull String operation, @NotNull Object arguments, @NotNull Class<T> responseType) {
		return connection.request(operation, arguments, responseType);
	}

	@Override
	public void close() {
		connection.close();
	}

}
