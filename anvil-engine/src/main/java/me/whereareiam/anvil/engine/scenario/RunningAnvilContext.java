package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.runtime.AnvilContext;
import me.whereareiam.anvil.api.runtime.RunningProcess;
import me.whereareiam.anvil.api.runtime.RunningProxy;
import me.whereareiam.anvil.api.runtime.RunningServer;
import me.whereareiam.anvil.engine.runtime.process.ManagedProcess;
import me.whereareiam.anvil.engine.provisioning.WorkspaceSession;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.engine.AnvilException;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Default running context owned by an {@code AnvilEngine}.
 */
public final class RunningAnvilContext implements AnvilContext {
	private final AnvilScenario scenario;
	private final PlayerManager players;
	private final ScenarioResources resources;

	/**
	 * Creates a context from already started processes and a context-owned player manager.
	 */
	public RunningAnvilContext(
			AnvilScenario scenario,
			Map<String, ManagedProcess> processes,
			PlayerManager players,
			Duration stopTimeout,
			List<WorkspaceSession> workspaces
	) {
		this(scenario, processes, players, stopTimeout, workspaces, successful -> { });
	}

	/**
	 * Creates a context with a callback for the generated scenario-run directory.
	 */
	public RunningAnvilContext(
			AnvilScenario scenario,
			Map<String, ManagedProcess> processes,
			PlayerManager players,
			Duration stopTimeout,
			List<WorkspaceSession> workspaces,
			Consumer<Boolean> runFinalizer
	) {
		this(scenario, players, new ScenarioResources(stopTimeout, runFinalizer));
		processes.values().forEach(resources::addProcess);
		workspaces.forEach(resources::addWorkspace);
	}

	/**
	 * Takes ownership of the resources accumulated during scenario startup.
	 */
	public RunningAnvilContext(
			@NotNull AnvilScenario scenario,
			@NotNull PlayerManager players,
			@NotNull ScenarioResources resources
	) {
		this.scenario = scenario;
		this.players = players;
		this.resources = resources;
		resources.players(players);
	}

	@Override
	public @NotNull AnvilScenario scenario() {
		return scenario;
	}

	@Override
	public @NotNull Collection<RunningProcess> processes() {
		return Collections.unmodifiableCollection(new ArrayList<>(resources.processes().values()));
	}

	@Override
	public @NotNull RunningProcess process(@NotNull String name) {
		Map<String, ManagedProcess> processes = resources.processes();
		ManagedProcess process = processes.get(name);
		if (process == null)
			throw new AnvilException("Unknown process '" + name + "'. Available: " + processes.keySet());
		return process;
	}

	@Override
	public @NotNull Collection<RunningServer> servers() {
		return resources.processes().values().stream()
				.filter(RunningServer.class::isInstance)
				.map(RunningServer.class::cast)
				.toList();
	}

	@Override
	public @NotNull RunningServer server(@NotNull String name) {
		RunningProcess process = process(name);
		if (!(process instanceof RunningServer server))
			throw new AnvilException("Process '" + name + "' is not a Minecraft server");
		return server;
	}

	@Override
	public @NotNull Collection<RunningProxy> proxies() {
		return resources.processes().values().stream()
				.filter(RunningProxy.class::isInstance)
				.map(RunningProxy.class::cast)
				.toList();
	}

	@Override
	public @NotNull RunningProxy proxy(@NotNull String name) {
		RunningProcess process = process(name);
		if (!(process instanceof RunningProxy proxy))
			throw new AnvilException("Process '" + name + "' is not a Minecraft proxy");
		return proxy;
	}

	@Override
	public @NotNull PlayerManager players() {
		return players;
	}

	@Override
	public void close() {
		close(true);
	}

	/**
	 * Stops this context and finalizes workspaces with explicit success semantics.
	 *
	 * @param successful whether the scenario completed normally
	 */
	@Override
	public void close(boolean successful) {
		resources.close(successful);
	}
}
