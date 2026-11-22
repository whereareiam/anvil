package me.whereareiam.anvil.capability.network;

import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.network.agent.NetworkOperations;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Connects the public network capability to the network agent extension of a NeoForge server, the one platform whose
 * network has payload channels. It installs into NeoForge processes the agent channel and every part of the NeoForge
 * side, of which the part for the server's Minecraft release installs.
 */
public final class NetworkProvider implements AgentProcessCapabilityProvider<Network> {
	private static final Set<String> NEOFORGE = Set.of(Platforms.NEOFORGE);
	private static final List<String> NEOFORGE_OPERATIONS = List.of(
			"me.whereareiam.anvil.capability.network.neoforge.v1_21_1.NeoForgeNetworkOperations",
			"me.whereareiam.anvil.capability.network.neoforge.v1_21_11.NeoForgeNetworkOperations"
	);

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("anvil.network").build();
	}

	@Override
	public @NotNull Class<Network> capability() {
		return Network.class;
	}

	@Override
	public boolean supportsPlatform(@NotNull String platformId) {
		return NEOFORGE.contains(platformId);
	}

	@Override
	public @NotNull Set<AgentExtension> agentExtensions() {
		Set<AgentExtension> extensions = new LinkedHashSet<>();
		extensions.add(AgentExtension.builder().platforms(NEOFORGE).entrypoint(NetworkOperations.class.getName()).build());
		for (String operations : NEOFORGE_OPERATIONS)
			extensions.add(AgentExtension.builder().platforms(NEOFORGE).entrypoint(operations).build());

		return Set.copyOf(extensions);
	}

	@Override
	public @NotNull Network create(@NotNull AgentProcessCapabilityContext context) {
		return new AgentNetwork(context.channel());
	}
}
