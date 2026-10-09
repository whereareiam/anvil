package me.whereareiam.anvil.launcher.assembly.player;

import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.launcher.assembly.ProviderDiscovery;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Selects the installed composition provider and binds scenario agents to the built-in composer.
 */
public final class PlayerComposition {
	/**
	 * Discovers composition in provider order, preserving standalone protocol composer implementations.
	 * The built-in composer validates the capability graph of every listed library before players exist
	 * and the graph of any other library when its first player is composed.
	 *
	 * @param libraries libraries the scenario's players use unless they declare their own
	 * @param agents borrowed scenario agent directory
	 * @return composer bound to this scenario
	 */
	public static @NotNull ProtocolPlayerComposer create(@NotNull Collection<String> libraries, @NotNull AgentDirectory agents) {
		var provider = new ProviderDiscovery().required(ProtocolPlayerComposerProvider.class, "protocol-player composer");

		return create(libraries, agents, provider);
	}

	static @NotNull ProtocolPlayerComposer create(
			@NotNull Collection<String> libraries,
			@NotNull AgentDirectory agents,
			@NotNull ProtocolPlayerComposerProvider provider
	) {
		if (provider instanceof CapabilityPlayerComposerProvider capabilities)
			return capabilities.create(libraries, agents);

		return provider.create();
	}
}
