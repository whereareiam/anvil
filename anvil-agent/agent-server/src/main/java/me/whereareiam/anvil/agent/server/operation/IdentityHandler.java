package me.whereareiam.anvil.agent.server.operation;

import me.whereareiam.anvil.agent.api.model.location.AgentLocation;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentIdentityRequest;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentIdentityResponse;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentLocationPayload;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.api.type.AgentLocationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the platform's observed identity and encodes its shared location payload.
 */
public final class IdentityHandler implements AgentOperationHandler<AgentIdentityRequest, AgentIdentityResponse> {
	@Override
	public @Nullable AgentIdentityResponse execute(@NotNull PlatformAgent platform, @NotNull AgentIdentityRequest request) {
		var identity = platform.identity(request.getUsername());
		if (identity.isEmpty()) return null;

		var value = identity.get();
		return AgentIdentityResponse.builder()
				.username(value.getUsername())
				.uniqueId(value.getUniqueId().toString())
				.location(toPayload(value.getLocation()))
				.build();
	}

	private @NotNull AgentLocationPayload toPayload(@NotNull AgentLocation location) {
		if (location instanceof ServerLocation server)
			return AgentLocationPayload.builder()
					.type(AgentLocationType.SERVER)
					.server(server.getServer())
					.build();

		ProxyLocation proxy = (ProxyLocation) location;
		return AgentLocationPayload.builder()
				.type(AgentLocationType.PROXY)
				.proxy(proxy.getProxy())
				.connectedServer(proxy.getConnectedServer())
				.build();
	}
}
