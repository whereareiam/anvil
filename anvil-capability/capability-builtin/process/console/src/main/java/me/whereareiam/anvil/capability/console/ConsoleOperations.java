package me.whereareiam.anvil.capability.console;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;

/**
 * Typed agent operations the console capability sends through its process's request channel.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConsoleOperations {
	/**
	 * Dispatches a command through the platform console of the process the agent runs in.
	 */
	public static final ChannelOperation<AgentCommandRequest, AgentCommandResponse> COMMAND = new ChannelOperation<>(
			AgentOperations.COMMAND.getName(),
			AgentOperations.COMMAND.getRequestType(),
			AgentOperations.COMMAND.getResponseType()
	);
}
