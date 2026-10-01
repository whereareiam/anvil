package me.whereareiam.anvil.api.process;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.process.type.RunningProxy;
import me.whereareiam.anvil.api.process.type.RunningServer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * Scenario-owned process lookup and individual lifecycle operations.
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
	 * Returns an immutable snapshot of all created server and proxy process generations.
	 * Prepared processes that have never started are omitted; stopped generations remain available for diagnostics.
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
	 * @throws IllegalStateException  if the declared process has never started
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
	 * @throws NoSuchElementException   if the name is unknown
	 * @throws IllegalStateException    if the declared process has never started
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
	 * @throws NoSuchElementException   if the name is unknown
	 * @throws IllegalStateException    if the declared process has never started
	 * @throws IllegalArgumentException if the name identifies a server
	 */
	@NotNull RunningProxy proxy(@NotNull String name);

	/**
	 * Starts one prepared process without starting its peers or executing the scenario setup hook.
	 * The complete topology, forwarding configuration, workspace, and listener address are retained.
	 * A ready process returns its existing handle; a stopped process creates a new generation and
	 * reapplies platform configuration. Failed startup stops its new generation and marks the scenario unsuccessful.
	 *
	 * @param name scenario process name
	 * @return current generation after readiness and agent connection complete
	 * @throws NoSuchElementException if the process name is unknown
	 * @throws IllegalStateException  if the scenario is closed
	 * @throws ProcessException       if startup or shutdown fails
	 */
	@NotNull RunningProcess start(@NotNull String name);

	/**
	 * Stops one process and its platform agent while retaining its prepared workspace and address.
	 * Other processes and registered players remain owned by the scenario. Clients disconnected by
	 * the stop must explicitly reconnect after a subsequent start. Stopping an unstarted process has no effect.
	 *
	 * @param name scenario process name
	 * @throws NoSuchElementException if the process name is unknown
	 * @throws IllegalStateException  if the scenario is closed
	 * @throws ProcessException       if shutdown fails
	 */
	void stop(@NotNull String name);

	/**
	 * Restarts one managed process with its current workspace and listener address.
	 * Other processes remain running. Existing players remain registered, but clients
	 * disconnected by the restart must explicitly reconnect. The platform agent is
	 * reauthenticated before this method returns. Process capability instances are retained;
	 * agent-backed operations use the replacement connection.
	 *
	 * <p>The returned handle represents the new process generation. Previous process
	 * handles remain stopped; obtain fresh console checkpoints from the returned handle.
	 * Assets and caches are not reinstalled. Platform-owned configuration is reapplied.
	 * A failed restart marks the scenario unsuccessful even if its exception is caught.</p>
	 *
	 * @param name scenario process name
	 * @return replacement process after readiness and agent connection complete
	 * @throws NoSuchElementException if the process name is unknown
	 * @throws IllegalStateException  if the scenario is closed
	 * @throws ProcessException       if process startup or shutdown fails
	 */
	@NotNull RunningProcess restart(@NotNull String name);
}
