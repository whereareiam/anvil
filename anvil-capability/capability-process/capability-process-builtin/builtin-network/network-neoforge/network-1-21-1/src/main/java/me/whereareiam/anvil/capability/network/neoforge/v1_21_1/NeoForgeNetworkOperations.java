package me.whereareiam.anvil.capability.network.neoforge.v1_21_1;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.neoforge.api.PayloadRegistrations;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.MinecraftRelease;
import me.whereareiam.anvil.capability.network.agent.NetworkAgentOperations;
import me.whereareiam.anvil.capability.network.agent.model.AgentChannel;
import me.whereareiam.anvil.capability.network.agent.model.AgentChannelFlow;
import me.whereareiam.anvil.capability.network.agent.model.AgentChannelPhase;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.registration.PayloadRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Answers the network capability's operations on the NeoForge servers of Minecraft 1.21.1 until 1.21.11 with the
 * channels NeoForge registered, as the NeoForge agent reads them: the configuration and play channels, each at its
 * registered version, direction and optionality.
 */
public final class NeoForgeNetworkOperations extends NetworkAgentOperations {
	private static final Map<ConnectionProtocol, AgentChannelPhase> PHASES = Map.of(
			ConnectionProtocol.CONFIGURATION, AgentChannelPhase.CONFIGURATION,
			ConnectionProtocol.PLAY, AgentChannelPhase.PLAY
	);
	private static final Map<PacketFlow, AgentChannelFlow> FLOWS = Map.of(
			PacketFlow.CLIENTBOUND, AgentChannelFlow.CLIENTBOUND,
			PacketFlow.SERVERBOUND, AgentChannelFlow.SERVERBOUND
	);

	@Override
	public boolean supports(@NotNull AgentInfo platform) {
		return MinecraftRelease.within(platform.getVersion(), "1.21.1", "1.21.11");
	}

	@Override
	protected @NotNull List<AgentChannel> channels(@NotNull PlatformAgent platform) {
		return platform.requireService(PayloadRegistrations.class).all().entrySet().stream()
				.filter(phase -> PHASES.containsKey(phase.getKey()))
				.flatMap(phase -> phase.getValue().values().stream().map(registration -> channel(PHASES.get(phase.getKey()), registration)))
				.toList();
	}

	private static AgentChannel channel(AgentChannelPhase phase, PayloadRegistration<?> registration) {
		return AgentChannel.builder()
				.id(registration.id().toString())
				.version(registration.version())
				.phase(phase)
				.flow(registration.flow().map(FLOWS::get).orElse(null))
				.optional(registration.optional())
				.build();
	}
}
