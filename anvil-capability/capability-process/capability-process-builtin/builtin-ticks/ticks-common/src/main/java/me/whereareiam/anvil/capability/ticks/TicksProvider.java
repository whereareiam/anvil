package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.event.lifecycle.ProcessRestarted;
import me.whereareiam.anvil.capability.api.event.scenario.ScenarioFinished;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.ticks.agent.TicksOperations;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Connects the public ticks capability to the ticks agent extension of its server's platform. It serves the platforms
 * the ticks family has a platform side for, NeoForge's, and installs into their processes the agent channel together
 * with that platform side.
 */
public final class TicksProvider implements AgentProcessCapabilityProvider<Ticks> {
	private static final Set<String> SERVERS = Set.of(Platforms.NEOFORGE);
	private static final String NEOFORGE_OPERATIONS = "me.whereareiam.anvil.capability.ticks.neoforge.NeoForgeTicksOperations";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("anvil.ticks").build();
	}

	@Override
	public @NotNull Class<Ticks> capability() {
		return Ticks.class;
	}

	@Override
	public boolean supportsPlatform(@NotNull String platformId) {
		return SERVERS.contains(platformId);
	}

	@Override
	public @NotNull Set<AgentExtension> agentExtensions() {
		return Set.of(
				AgentExtension.builder().platforms(SERVERS).entrypoint(TicksOperations.class.getName()).build(),
				AgentExtension.builder().platforms(SERVERS).entrypoint(NEOFORGE_OPERATIONS).build()
		);
	}

	@Override
	public @NotNull Ticks create(@NotNull AgentProcessCapabilityContext context) {
		OpenPaces paces = new OpenPaces(context.processName(), context.channel());
		context.on(ScenarioFinished.class, finished -> paces.closeAll());
		context.on(ProcessRestarted.class, restarted -> paces.restarted());

		return new AgentTicks(context.processName(), context.channel(), paces);
	}
}
