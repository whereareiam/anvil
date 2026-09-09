package me.whereareiam.anvil.launcher.assembly.player;

import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.player.AgentPlayerCapabilityProviderAdapter;
import me.whereareiam.anvil.capability.player.PlayerCapabilityRuntime;
import me.whereareiam.anvil.launcher.assembly.process.AgentRequestChannel;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Composes independently owned native and agent-backed capabilities for one scenario.
 */
public final class CapabilityPlayerComposerProvider implements ProtocolPlayerComposerProvider {
	@Override
	public @NotNull String id() {
		return "capabilities";
	}

	/**
	 * Creates capability composition with an empty agent directory for standalone protocol consumers.
	 *
	 * @param protocolId selected protocol backend
	 * @return composer without scenario agent access
	 */
	@Override
	public @NotNull ProtocolPlayerComposer create(@NotNull String protocolId) {
		return create(protocolId, Map::of);
	}

	@NotNull ProtocolPlayerComposer create(@NotNull String protocolId, @NotNull AgentDirectory agents) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null) loader = CapabilityPlayerComposerProvider.class.getClassLoader();

		List<PlayerCapabilityProvider<?>> adapted = new ArrayList<>();
		for (AgentPlayerCapabilityProvider<?> provider : ServiceLoader.load(AgentPlayerCapabilityProvider.class, loader))
			adapted.add(new AgentPlayerCapabilityProviderAdapter<>(provider, processName ->
					agents.find(processName).<RequestChannel>map(AgentRequestChannel::new)
							.orElseThrow(() -> new CapabilityException("Process '" + processName + "' has no agent request channel"))));

		PlayerCapabilityRuntime capabilities = PlayerCapabilityRuntime.discover(protocolId, adapted);
		return (player, observation, onDestroyed) -> capabilities.compose(
				new ProtocolPlayerAdapter(player), observation, onDestroyed);
	}
}
