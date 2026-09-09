package me.whereareiam.anvil.agent.client.transport.connection;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.location.AgentLocation;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentIdentityRequest;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentIdentityResponse;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentLocationPayload;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import me.whereareiam.anvil.agent.api.type.AgentLocationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * JSON-backed implementation of the typed platform-agent contract.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class JsonAgentClient implements AgentClient {
	private final @NotNull AgentConnection connection;

	@Override
	public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
		var request = AgentIdentityRequest.builder().username(username).build();
		return Optional.ofNullable(connection.request(AgentOperations.IDENTITY, request)).map(this::identity);
	}

	@Override
	public boolean executeCommand(@NotNull String command) {
		var request = AgentCommandRequest.builder().command(command).build();
		var response = connection.request(AgentOperations.COMMAND, request);
		if (response == null) throw new AgentException("Missing agent command response");

		return response.isAccepted();
	}

	@Override
	public <T> @Nullable T request(@NotNull String operation, @Nullable Object arguments, @NotNull Class<T> responseType) {
		return connection.request(operation, arguments, responseType);
	}

	@Override
	public void close() {
		connection.close();
	}

	private @NotNull AgentIdentity identity(@NotNull AgentIdentityResponse response) {
		return AgentIdentity.builder()
				.username(response.getUsername())
				.uniqueId(UUID.fromString(response.getUniqueId()))
				.location(location(response.getLocation()))
				.build();
	}

	private @NotNull AgentLocation location(@NotNull AgentLocationPayload value) {
		if (value.getType() == AgentLocationType.SERVER)
			return ServerLocation.builder().server(value.getServer()).build();
		if (value.getType() == AgentLocationType.PROXY)
			return ProxyLocation.builder()
					.proxy(value.getProxy())
					.connectedServer(value.getConnectedServer())
					.build();

		throw new IllegalStateException("Unknown agent location type: " + value.getType());
	}
}
