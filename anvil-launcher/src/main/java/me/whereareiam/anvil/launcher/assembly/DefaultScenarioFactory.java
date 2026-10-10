package me.whereareiam.anvil.launcher.assembly;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.AgentPlayerObservation;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
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
import me.whereareiam.anvil.platform.api.PlatformPlanner;
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

	@Override
	public @NotNull ScenarioContext create(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) {
		var plan = platforms.plan(scenario);
		players.prepare(plan.getScenario());

		var agents = new ScenarioAgentDirectory();
		var capabilities = ProcessComposition.discover(plan, agents);
		var composer = PlayerComposition.create(players.scenarioLibraries(plan.getScenario()), agents);
		ProcessGroup processes = execution.prepare(plan, agents, capabilities, observer);

		try {
			processes = capabilities.bind(processes);
			var serverNames = plan.getScenario().getServers().stream()
					.map(MinecraftProcess::getName)
					.collect(Collectors.toSet());
			var manager = players.open(plan.getScenario(), processes,
					(player, connectedTo) -> new AgentPlayerObservation(player.name(), connectedTo, player::identity, agents, serverNames),
					composer);

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
