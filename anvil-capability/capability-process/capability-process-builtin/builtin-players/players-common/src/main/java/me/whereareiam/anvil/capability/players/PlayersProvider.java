package me.whereareiam.anvil.capability.players;

import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Connects the public players capability to the joins and leaves its process's platform agent reports.
 */
public final class PlayersProvider implements AgentProcessCapabilityProvider<Players> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("anvil.players").build();
	}

	@Override
	public @NotNull Class<Players> capability() {
		return Players.class;
	}

	@Override
	public @NotNull Players create(@NotNull AgentProcessCapabilityContext context) {
		return new ReportedPlayers(context.processName(), context.platform());
	}
}
