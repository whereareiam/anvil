package me.whereareiam.anvil.capability.console;

import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Connects the public console capability to the console command its process's platform agent runs.
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
		return command -> context.platform().execute(command);
	}
}
