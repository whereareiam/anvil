package me.whereareiam.anvil.agent.server.operation;

import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import org.jetbrains.annotations.NotNull;

/**
 * Executes a decoded command using the platform's console access.
 */
public final class CommandHandler implements AgentOperationHandler<AgentCommandRequest, AgentCommandResponse> {
	@Override
	public @NotNull AgentCommandResponse execute(@NotNull PlatformAgent platform, @NotNull AgentCommandRequest request) {
		return AgentCommandResponse.builder()
				.accepted(platform.executeCommand(request.getCommand()))
				.build();
	}
}
