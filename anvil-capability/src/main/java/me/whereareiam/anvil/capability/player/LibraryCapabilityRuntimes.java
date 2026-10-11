package me.whereareiam.anvil.capability.player;

import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Holds one player capability runtime per protocol library for a scenario. Every runtime combines the
 * library's own providers with the installed agent-backed ones, which reach their process through a
 * request channel.
 */
public final class LibraryCapabilityRuntimes {
	private final ClassLoader loader;
	private final List<PlayerCapabilityProvider<?>> agentBacked = new ArrayList<>();
	private final Map<String, PlayerCapabilityRuntime> runtimes = new ConcurrentHashMap<>();

	/**
	 * Discovers the agent-backed providers and validates the capability graph of the given libraries now;
	 * the runtime of any other library is discovered when a player first uses it.
	 *
	 * @param libraries protocol libraries the scenario's players select by default
	 * @param channels supplies the request channel of a process's agent, by process name
	 */
	public LibraryCapabilityRuntimes(@NotNull Collection<String> libraries, @NotNull Function<String, RequestChannel> channels) {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		loader = context == null ? LibraryCapabilityRuntimes.class.getClassLoader() : context;
		for (AgentPlayerCapabilityProvider<?> provider : ServiceLoader.load(AgentPlayerCapabilityProvider.class, loader))
			agentBacked.add(new AgentPlayerCapabilityProviderAdapter<>(provider, channels));

		for (String library : libraries) of(library);
	}

	/**
	 * Returns the runtime of a protocol library, discovering it on first use.
	 *
	 * @param libraryId protocol library identifier
	 * @return the library's capability runtime
	 */
	public @NotNull PlayerCapabilityRuntime of(@NotNull String libraryId) {
		return runtimes.computeIfAbsent(libraryId, library -> PlayerCapabilityRuntime.discover(loader, library, agentBacked));
	}
}
