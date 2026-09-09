package me.whereareiam.anvil.agent.server.operation;

import me.whereareiam.anvil.agent.api.model.transport.AgentPingResponse;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Reports platform identity after the endpoint authenticates a readiness request.
 */
public final class PingHandler implements AgentOperationHandler<Object, AgentPingResponse> {
	@Override
	public @NotNull AgentPingResponse execute(@NotNull PlatformAgent platform, @NotNull Object request) {
		var info = platform.info();
		return AgentPingResponse.builder()
				.platform(info.getPlatform())
				.version(info.getVersion())
				.role(info.getRole().name().toLowerCase(Locale.ROOT))
				.build();
	}
}
