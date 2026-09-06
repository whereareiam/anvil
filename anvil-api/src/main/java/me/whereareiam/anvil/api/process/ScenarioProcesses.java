package me.whereareiam.anvil.api.process;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.process.type.RunningProxy;
import me.whereareiam.anvil.api.process.type.RunningServer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * Scenario-owned process lookup and restart operations.
 * Lookups return the current generation; collection results are immutable snapshots.
 *
 * <pre>{@code
 * ScenarioProcesses processes = anvil.processes();
 * RunningProxy proxy = processes.proxy("proxy");
 * RunningProcess replacement = processes.restart(proxy.name());
 * }</pre>
 */
public interface ScenarioProcesses {
	/**
	 * Returns an immutable snapshot of all managed server and proxy processes.
	 *
	 * @return managed processes
	 */
	@NotNull Collection<RunningProcess> all();

	/**
	 * Resolves a server or proxy process by name.
	 *
	 * @param name scenario process name
	 * @return matching process
	 * @throws NoSuchElementException if the process name is unknown
	 */
	@NotNull RunningProcess get(@NotNull String name);

	/**
	 * Returns an immutable snapshot of all managed Minecraft servers.
	 *
	 * @return managed servers
	 */
	@NotNull Collection<RunningServer> servers();

	/**
	 * Resolves a managed Minecraft server.
	 *
	 * @param name scenario server name
	 * @return matching running server
	 * @throws NoSuchElementException if the name is unknown
	 * @throws IllegalArgumentException if the name identifies a proxy
	 */
	@NotNull RunningServer server(@NotNull String name);

	/**
	 * Returns an immutable snapshot of all managed Minecraft proxies.
	 *
	 * @return managed proxies
	 */
	@NotNull Collection<RunningProxy> proxies();

	/**
	 * Resolves a managed Minecraft proxy.
	 *
	 * @param name scenario proxy name
	 * @return matching running proxy
	 * @throws NoSuchElementException if the name is unknown
	 * @throws IllegalArgumentException if the name identifies a server
	 */
	@NotNull RunningProxy proxy(@NotNull String name);

	/**
	 * Restarts one managed process with its current workspace and listener address.
	 * Other processes remain running. Existing players remain registered, but clients
	 * disconnected by the restart must explicitly reconnect. The platform agent is
	 * reauthenticated before this method returns and borrowed agent handles are refreshed.
	 *
	 * <p>The returned handle represents the new process generation. Previous process
	 * handles remain stopped; obtain fresh console checkpoints from the returned handle.
	 * Assets and caches are not reinstalled. Platform-owned configuration is reapplied.
	 * A failed restart marks the scenario unsuccessful even if its exception is caught.</p>
	 *
	 * @param name scenario process name
	 * @return replacement process after readiness and agent connection complete
	 * @throws NoSuchElementException if the process name is unknown
	 * @throws IllegalStateException if the scenario is closed
	 * @throws ProcessException if process startup or shutdown fails
	 */
	@NotNull RunningProcess restart(@NotNull String name);
}
