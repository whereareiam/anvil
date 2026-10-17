package me.whereareiam.anvil.capability.dispatch;

import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Connects the public dispatch capability to the console command its process's platform agent runs.
 */
public final class DispatchProvider implements AgentProcessCapabilityProvider<Dispatch> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("anvil.dispatch").build();
	}

	@Override
	public @NotNull Class<Dispatch> capability() {
		return Dispatch.class;
	}

	@Override
	public @NotNull Dispatch create(@NotNull AgentProcessCapabilityContext context) {
		return command -> context.platform().execute(command);
	}
}
