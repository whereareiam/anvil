package me.whereareiam.anvil.launcher.assembly;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.AgentPlayerObservation;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioFactory;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.engine.scenario.RunningScenario;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.launcher.assembly.player.PlayerComposition;
import me.whereareiam.anvil.launcher.assembly.process.ProcessComposition;
import me.whereareiam.anvil.launcher.assembly.retention.ProcessLease;
import me.whereareiam.anvil.launcher.assembly.retention.RetainedProcesses;
import me.whereareiam.anvil.platform.api.PlatformPlanner;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.player.DefaultPlayerService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

/**
 * Prepares each scenario through scoped services and joins its process and player lifetimes.
 */
@RequiredArgsConstructor
public final class DefaultScenarioFactory implements ScenarioFactory {
	private final @NotNull PlatformPlanner platforms;
	private final @NotNull ProcessLauncher execution;
	private final @NotNull DefaultPlayerService players;
	private final @NotNull RetainedProcesses retained;

	@Override
	public @NotNull ScenarioContext create(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) {
		var plan = platforms.plan(scenario);
		players.prepare(plan.getScenario());

		// Processes with the engine lifetime already run; the scenario's own are prepared against them.
		ProcessLease lease = retained.lease(plan);
		PlatformPlan own = lease.ownPlan();
		ScenarioAgentDirectory agents = new ScenarioAgentDirectory();
		AgentDirectory reachable = lease.agents(agents);
		ProcessComposition capabilities;
		ProtocolPlayerComposer composer;
		ProcessGroup processes;
		try {
			capabilities = ProcessComposition.discover(own, agents);
			composer = PlayerComposition.create(players.scenarioLibraries(plan.getScenario()), reachable);
			processes = lease.join(execution.prepare(own, agents, capabilities, observer, lease.addresses()));
		} catch (RuntimeException | Error failure) {
			try {
				lease.release(true);
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}

		try {
			processes = capabilities.bind(processes);
			lease.announce(observer);
			var serverNames = plan.getScenario().getServers().stream()
					.map(MinecraftProcess::getName)
					.collect(Collectors.toSet());
			var manager = players.open(plan.getScenario(), processes,
					(player, connectedTo) -> new AgentPlayerObservation(player.name(), connectedTo, player::identity, reachable, serverNames),
					composer, execution.preservesClientAddress(plan));

			return new RunningScenario(plan.getScenario(), processes, manager);
		} catch (RuntimeException | Error failure) {
			try {
				processes.finish(false);
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}
}
