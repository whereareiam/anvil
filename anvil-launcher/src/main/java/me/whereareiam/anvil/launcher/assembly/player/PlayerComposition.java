package me.whereareiam.anvil.launcher.assembly.player;

import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.launcher.assembly.ProviderDiscovery;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Selects the installed composition provider and binds scenario agents to the built-in composer.
 */
public final class PlayerComposition {
	/**
	 * Discovers composition in provider order, preserving standalone protocol composer implementations.
	 *
	 * @param protocolId selected backend ID
	 * @param agents borrowed scenario agent directory
	 * @return composer bound to this scenario
	 */
	public static @NotNull ProtocolPlayerComposer create(@NotNull String protocolId, @NotNull AgentDirectory agents) {
		var provider = new ProviderDiscovery().required(ProtocolPlayerComposerProvider.class, "protocol-player composer");

		return create(protocolId, agents, provider);
	}

	static @NotNull ProtocolPlayerComposer create(
			@NotNull String protocolId,
			@NotNull AgentDirectory agents,
			@NotNull ProtocolPlayerComposerProvider provider
	) {
		if (provider instanceof CapabilityPlayerComposerProvider capabilities)
			return capabilities.create(protocolId, agents);

		return provider.create(protocolId);
	}
}
