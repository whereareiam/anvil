package me.whereareiam.anvil.agent.common.transport.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.location.AgentLocation;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentIdentityRequest;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentIdentityResponse;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentLocationPayload;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnection;
import me.whereareiam.anvil.agent.api.type.AgentLocationType;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and encodes a player identity observed by the platform.
 */
public final class IdentityOperation implements AgentRequestHandler {
	private static final String WIRE_NAME = "identity";
	private final ObjectMapper mapper = new ObjectMapper();

	public @NotNull Optional<AgentIdentity> request(
			@NotNull AgentConnection connection,
			@NotNull String username
	) {
		AgentIdentityRequest arguments = AgentIdentityRequest.builder().username(username).build();
		AgentIdentityResponse result = connection.request(WIRE_NAME, arguments, AgentIdentityResponse.class);
		return Optional.ofNullable(result).map(this::parse);
	}

	@Override
	public @NotNull String wireName() {
		return WIRE_NAME;
	}

	@Override
	public @NotNull JsonNode handle(@NotNull PlatformAgent platformAgent, @NotNull JsonNode arguments) {
		AgentIdentityRequest request = mapper.convertValue(arguments, AgentIdentityRequest.class);
		Optional<AgentIdentity> identity = platformAgent.identity(request.getUsername());
		if (identity.isEmpty())
			return mapper.nullNode();
		AgentIdentity value = identity.get();
		return mapper.valueToTree(AgentIdentityResponse.builder()
				.username(value.getUsername())
				.uniqueId(value.getUniqueId().toString())
				.location(toPayload(value.getLocation()))
				.build());
	}

	private static AgentLocationPayload toPayload(AgentLocation location) {
		if (location instanceof ServerLocation server)
			return AgentLocationPayload.builder().type(AgentLocationType.SERVER).server(server.getServer()).build();
		ProxyLocation proxy = (ProxyLocation) location;
		return AgentLocationPayload.builder().type(AgentLocationType.PROXY).proxy(proxy.getProxy())
				.connectedServer(proxy.getConnectedServer()).build();
	}

	private AgentIdentity parse(AgentIdentityResponse response) {
		return AgentIdentity.builder()
				.username(response.getUsername())
				.uniqueId(UUID.fromString(response.getUniqueId()))
				.location(parseLocation(response.getLocation()))
				.build();
	}

	private static AgentLocation parseLocation(AgentLocationPayload value) {
		if (value.getType() == AgentLocationType.SERVER)
			return ServerLocation.builder().server(value.getServer()).build();
		if (value.getType() == AgentLocationType.PROXY)
			return ProxyLocation.builder().proxy(value.getProxy())
					.connectedServer(value.getConnectedServer()).build();
		throw new IllegalStateException("Unknown agent location type: " + value.getType());
	}
}
