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
	 * Restarts one managed process with its current workspace and listener address.
	 * Other processes remain running. Existing players remain registered, but clients
	 * disconnected by the restart must explicitly reconnect. The platform agent is
	 * reauthenticated before this method returns and borrowed agent handles are refreshed.
	 *
	 * <p>The returned handle represents the new process generation. Previous process
	 * handles remain stopped; obtain fresh console cursors from the returned handle.
	 * Assets and caches are not reinstalled. Platform-owned configuration is reapplied.
	 * A failed restart marks the scenario unsuccessful even if its exception is caught.</p>
	 *
	 * @param name scenario process name
	 * @return replacement process after readiness and agent connection complete
	 */
	@NotNull RunningProcess restart(@NotNull String name);

	/**
	 * Destroys players and stops every process in reverse launch order.
	 */
	@Override
	void close();

	/**
	 * Stops all resources and finalizes the run with its test or application outcome.
	 * Failed runs retain diagnostic workspaces according to engine policy and do not
	 * save success caches. Cleanup failures also make the run unsuccessful.
	 *
	 * @param successful whether the scenario's test or application completed successfully
	 */
	void close(boolean successful);
}
