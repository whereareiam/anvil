package me.whereareiam.anvil.agent.common.transport.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.agent.api.model.transport.AgentPingResponse;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

/**
 * Reports the platform-agent identity used during connection readiness checks.
 */
public final class PingOperation implements AgentRequestHandler {
	private static final String WIRE_NAME = "ping";
	private final ObjectMapper mapper = new ObjectMapper();

	public void request(@NotNull AgentConnection connection) {
		connection.request(WIRE_NAME, Map.of(), AgentPingResponse.class);
	}

	@Override
	public @NotNull String wireName() {
		return WIRE_NAME;
	}

	@Override
	public @NotNull JsonNode handle(@NotNull PlatformAgent platformAgent, @NotNull JsonNode arguments) {
		var info = platformAgent.info();
		return mapper.valueToTree(AgentPingResponse.builder()
				.platform(info.getPlatform())
				.version(info.getVersion())
				.role(info.getRole().name().toLowerCase(Locale.ROOT))
				.build());
	}
}
