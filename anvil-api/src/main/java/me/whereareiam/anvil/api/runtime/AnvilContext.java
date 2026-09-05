package me.whereareiam.anvil.api.runtime;

import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Running scenario supplied to tests, setup hooks, and manual runners.
 */
public interface AnvilContext extends AutoCloseable {
	/**
	 * Returns the scenario that produced this context.
	 *
	 * @return running scenario
	 */
	@NotNull AnvilScenario scenario();

	/**
	 * Returns all managed server and proxy processes.
	 *
	 * @return managed processes
	 */
	@NotNull Collection<RunningProcess> processes();

	/**
	 * Resolves a server or proxy process by name.
	 *
	 * @param name scenario process name
	 * @return matching process
	 */
	@NotNull RunningProcess process(@NotNull String name);

	/**
	 * Returns all managed Minecraft servers.
	 *
	 * @return managed servers
	 */
	@NotNull Collection<RunningServer> servers();

	/**
	 * Resolves a managed Minecraft server.
	 *
	 * @param name scenario server name
	 * @return matching running server
	 */
	@NotNull RunningServer server(@NotNull String name);

	/**
	 * Returns all managed Minecraft proxies.
	 *
	 * @return managed proxies
	 */
	@NotNull Collection<RunningProxy> proxies();

	/**
	 * Resolves a managed Minecraft proxy.
	 *
	 * @param name scenario proxy name
	 * @return matching running proxy
	 */
	@NotNull RunningProxy proxy(@NotNull String name);

	/**
	 * Returns the context-owned simulated-player factory and registry.
	 *
	 * @return player manager
	 */
	@NotNull PlayerManager players();

	/**
	 * Destroys players and stops every process in reverse launch order.
	 */
	@Override
	void close();
}
