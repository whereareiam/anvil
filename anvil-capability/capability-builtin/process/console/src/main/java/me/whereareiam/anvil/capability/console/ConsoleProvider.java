package me.whereareiam.anvil.capability.console;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Connects the public console capability to the owning process's native agent client.
 */
public final class ConsoleProvider implements AgentProcessCapabilityProvider<Console> {
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
			var response = channel.request(ConsoleOperations.COMMAND, request);
			if (response == null) throw new AgentException("Missing agent command response");

			return response.isAccepted();
		};
	}
}
