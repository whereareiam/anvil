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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composes independently owned native and agent-backed capabilities for one scenario, with one
 * capability runtime per protocol library.
 */
public final class CapabilityPlayerComposerProvider implements ProtocolPlayerComposerProvider {
	@Override
	public @NotNull String id() {
		return "capabilities";
	}

	/**
	 * Creates capability composition with an empty agent directory for standalone protocol consumers.
	 * Each library's capability runtime is created when its first player is composed.
	 *
	 * @return composer without scenario agent access
	 */
	@Override
	public @NotNull ProtocolPlayerComposer create() {
		return create(List.of(), Map::of);
	}

	/**
	 * Creates scenario composition and validates the capability graph of each listed library before
	 * any player exists. Other libraries' runtimes are created when their first player is composed.
	 *
	 * @param libraries protocol libraries whose capability graphs are validated immediately
	 * @param agents borrowed scenario agent directory
	 * @return composer bound to the scenario's agents
	 */
	@NotNull ProtocolPlayerComposer create(@NotNull Collection<String> libraries, @NotNull AgentDirectory agents) {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		ClassLoader loader = context == null ? CapabilityPlayerComposerProvider.class.getClassLoader() : context;

		List<PlayerCapabilityProvider<?>> adapted = new ArrayList<>();
		for (AgentPlayerCapabilityProvider<?> provider : ServiceLoader.load(AgentPlayerCapabilityProvider.class, loader))
			adapted.add(new AgentPlayerCapabilityProviderAdapter<>(provider, processName ->
					agents.find(processName).<RequestChannel>map(AgentRequestChannel::new)
							.orElseThrow(() -> new CapabilityException("Process '" + processName + "' has no agent request channel"))));

		Map<String, PlayerCapabilityRuntime> runtimes = new ConcurrentHashMap<>();
		for (String library : libraries)
			runtimes.put(library, PlayerCapabilityRuntime.discover(loader, library, adapted));

		return (player, observation, metadata, onDestroyed) -> runtimes
				.computeIfAbsent(player.libraryId(), library -> PlayerCapabilityRuntime.discover(loader, library, adapted))
				.compose(new ProtocolPlayerAdapter(player, metadata), observation, onDestroyed);
	}
}
