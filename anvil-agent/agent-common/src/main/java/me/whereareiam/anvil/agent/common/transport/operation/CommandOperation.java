package me.whereareiam.anvil.agent.common.transport.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnection;
import org.jetbrains.annotations.NotNull;

/**
 * Executes a command through the platform console.
 */
public final class CommandOperation implements AgentRequestHandler {
	private static final String WIRE_NAME = "command";
	private final ObjectMapper mapper = new ObjectMapper();

	public boolean request(@NotNull AgentConnection connection, @NotNull String command) {
		AgentCommandRequest arguments = AgentCommandRequest.builder().command(command).build();
		AgentCommandResponse response = connection.request(WIRE_NAME, arguments, AgentCommandResponse.class);
		if (response == null)
			throw new AgentException("Missing agent command response");
		return response.isAccepted();
	}

	@Override
	public @NotNull String wireName() {
		return WIRE_NAME;
	}

	@Override
	public @NotNull JsonNode handle(@NotNull PlatformAgent platformAgent, @NotNull JsonNode arguments) {
		AgentCommandRequest request = mapper.convertValue(arguments, AgentCommandRequest.class);
		return mapper.valueToTree(AgentCommandResponse.builder()
				.accepted(platformAgent.executeCommand(request.getCommand()))
				.build());
	}
}
