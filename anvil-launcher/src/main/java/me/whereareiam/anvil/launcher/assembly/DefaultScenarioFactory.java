package me.whereareiam.anvil.launcher.assembly;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioFactory;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.capability.process.ProcessCapabilityRuntime;
import me.whereareiam.anvil.engine.process.RetainedProcesses;
import me.whereareiam.anvil.engine.scenario.RunningScenario;
import me.whereareiam.anvil.launcher.adapter.process.ScenarioPlatforms;
import me.whereareiam.anvil.platform.api.PlatformPlanner;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.player.DefaultPlayerService;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.requirement.RequirementCheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Prepares each scenario through scoped services and joins its process and player lifetimes. A scenario the
 * workbench cannot run is refused before anything of it is planned.
 */
@RequiredArgsConstructor
public final class DefaultScenarioFactory implements ScenarioFactory {
	private final @NotNull Workbench workbench;
	private final @NotNull String defaultExecutionProviderId;
	private final @NotNull PlatformPlanner platforms;
	private final @NotNull ProcessLauncher execution;
	private final @NotNull DefaultPlayerService players;
	private final @NotNull RetainedProcesses<RetainedProcessSet> retained;

	/**
	 * Makes the session servers on this machine's loopback that processes verify logins with, such as a test's
	 * Yggdrasil stand-in, reachable where the workbench runs the processes.
	 */
	private void exposeSessionServers(AnvilScenario scenario) {
		Stream.concat(scenario.getServers().stream(), scenario.getProxies().stream())
				.map(MinecraftProcess::getSessionServer)
				.filter(server -> server != null && server.getHost() != null && server.getPort() > 0)
				.filter(server -> server.getHost().equals("localhost") || server.getHost().startsWith("127.") || server.getHost().equals("[::1]"))
				.forEach(server -> {
					try {
						workbench.expose(server.getPort());
					} catch (IOException failure) {
						throw new ProvisioningException("Could not make session server " + server + " reachable from workbench '"
								+ workbench.name() + "'", failure);
					}
				});
	}

	@Override
	public @NotNull ScenarioContext create(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) {
		RequirementCheck.require(scenario, defaultExecutionProviderId, workbench.facts());
		exposeSessionServers(scenario);
		var plan = platforms.plan(scenario);
		players.prepare(plan.getScenario());

		// Processes with the engine lifetime already run; the scenario's own are prepared against them.
		ProcessLease lease = ProcessLease.take(plan, retained, execution);
		PlatformPlan own = lease.ownPlan();
		ScenarioAgentDirectory agents = new ScenarioAgentDirectory();
		AgentDirectory reachable = lease.agents(agents);
		ScenarioPlatforms platforms = new ScenarioPlatforms(reachable);
		ProcessCapabilityRuntime capabilities;
		ProtocolPlayerComposer composer;
		ProcessGroup processes;
		try {
			capabilities = ProcessComposition.discover(own, agents, platforms);
			composer = PlayerComposition.create(players.scenarioLibraries(plan.getScenario()));
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
			var manager = players.open(plan.getScenario(), processes, composer, execution.preservesClientAddress(plan));

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
