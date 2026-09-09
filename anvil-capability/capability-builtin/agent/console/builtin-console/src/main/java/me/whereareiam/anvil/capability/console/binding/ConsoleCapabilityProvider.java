package me.whereareiam.anvil.capability.console.binding;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.console.Console;
import org.jetbrains.annotations.NotNull;

/**
 * Connects the public console capability to the owning process's native agent client.
 */
public final class ConsoleCapabilityProvider implements AgentProcessCapabilityProvider<Console> {
	private static final ChannelOperation<AgentCommandRequest, AgentCommandResponse> COMMAND = new ChannelOperation<>(
			AgentOperations.COMMAND.getName(),
			AgentOperations.COMMAND.getRequestType(),
			AgentOperations.COMMAND.getResponseType()
	);

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("anvil.console").build();
	}

	@Override
	public @NotNull Class<Console> capability() {
		return Console.class;
	}

	@Override
	public @NotNull Console create(@NotNull AgentProcessCapabilityContext context) {
		var channel = context.channel();
		return command -> {
			var request = AgentCommandRequest.builder().command(command).build();
			var response = channel.request(COMMAND, request);
			if (response == null) throw new AgentException("Missing agent command response");

			return response.isAccepted();
		};
	}
}
