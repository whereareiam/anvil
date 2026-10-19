package me.whereareiam.anvil.api.process;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.process.type.RunningContainer;
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
	 * Returns the running containers.
	 *
	 * @return running containers
	 */
	@NotNull Collection<RunningContainer> containers();

	/**
	 * Returns one running container.
	 *
	 * @param name scenario process name
	 * @return the container
	 * @throws NoSuchElementException if no container of that name runs
	 */
	@NotNull RunningContainer container(@NotNull String name);

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
	 * Kills one process without its shutdown sequence, as if it had crashed: its execution provider terminates it
	 * at once, so the platform runs no stop command, shutdown hooks or plugin disable logic. Use it to test what a
	 * plugin does when a server or proxy dies, such as recovering state, expiring locks or reconnecting players.
	 *
	 * <p>Everything else matches {@link #stop(String)}: the generation ends {@code STOPPED}, its platform agent and
	 * launch attachments are released, its workspace and address are retained, and other processes keep running.
	 * Clients connected to it lose their connection and must explicitly reconnect after a subsequent
	 * {@link #start(String)}, which creates a new generation. A deliberate kill does not mark the scenario
	 * unsuccessful; the console records it with an {@code [Anvil]} line. Killing an unstarted or stopped process has
	 * no effect.</p>
	 *
	 * <pre>{@code
	 * anvil.processes().kill("proxy");
	 * alice.capability(Session.class).disconnected();
	 * anvil.processes().start("proxy");
	 * }</pre>
	 *
	 * @param name scenario process name
	 * @throws NoSuchElementException if the process name is unknown
	 * @throws IllegalStateException  if the scenario is closed
	 * @throws ProcessException       if the process does not exit after it is killed
	 */
	void kill(@NotNull String name);

	/**
	 * Suspends one running process while it keeps its connections, as a server does that hangs: its players stay
	 * connected but it answers nobody, runs no tick and renews nothing until {@link #thaw(String) thawed}. Use it to
	 * test what a plugin does when a server stops answering without crashing, such as taking over its work once its
	 * leases expire.
	 *
	 * <p>Locally the process and its children are stopped by a signal, under Docker its container is paused. Stopping,
	 * killing or restarting a frozen process thaws it first. Freezing a frozen process has no effect.</p>
	 *
	 * <pre>{@code
	 * anvil.processes().freeze("lobby");
	 * // ... the proxy takes over within the lease time
	 * anvil.processes().thaw("lobby");
	 * }</pre>
	 *
	 * @param name scenario process name
	 * @throws NoSuchElementException        if the process name is unknown
	 * @throws IllegalStateException         if the scenario is closed or the process is not running
	 * @throws UnsupportedOperationException if the execution provider cannot suspend a process, with the reason
	 */
	void freeze(@NotNull String name);

	/**
	 * Resumes a {@link #freeze(String) frozen} process, which carries on where it stopped. Thawing a process that is
	 * not frozen has no effect.
	 *
	 * @param name scenario process name
	 * @throws NoSuchElementException if the process name is unknown
	 * @throws IllegalStateException  if the scenario is closed
	 */
	void thaw(@NotNull String name);

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
