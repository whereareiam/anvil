package me.whereareiam.anvil.launcher.assembly;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.AgentPlayerObservation;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioExecutor;
import me.whereareiam.anvil.launcher.assembly.process.ProcessComposition;
import me.whereareiam.anvil.engine.scenario.RunningScenario;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.launcher.assembly.player.PlayerComposition;
import me.whereareiam.anvil.platform.api.PlatformPlanner;
import me.whereareiam.anvil.protocol.player.DefaultPlayerService;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

/**
 * Starts the scoped services for one scenario and joins their process and player lifetimes.
 */
@RequiredArgsConstructor
public final class ScenarioLauncher implements ScenarioExecutor {
	private final @NotNull PlatformPlanner platforms;
	private final @NotNull ProcessLauncher execution;
	private final @NotNull DefaultPlayerService players;
	private final @NotNull String protocolId;

	@Override
	public @NotNull ScenarioContext open(@NotNull AnvilScenario scenario) {
		var plan = platforms.plan(scenario);
		var agents = new ScenarioAgentDirectory();
		var capabilities = ProcessComposition.discover(plan, agents);
		var composer = PlayerComposition.create(protocolId, agents);
		players.prepare();
		ProcessGroup processes = execution.start(plan, agents);
		try {
			processes = capabilities.bind(processes);
			var serverNames = plan.getScenario().getServers().stream()
					.map(MinecraftProcess::getName)
					.collect(Collectors.toSet());
			var manager = players.open(plan.getScenario(), processes,
					player -> new AgentPlayerObservation(player.name(), player::identity, agents, serverNames),
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
