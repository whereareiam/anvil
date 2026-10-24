package me.whereareiam.anvil.launcher.assembly;

import lombok.Getter;
import me.whereareiam.anvil.agent.client.api.AgentArtifactLocator;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioFactory;
import me.whereareiam.anvil.capability.AgentExtensions;
import me.whereareiam.anvil.engine.process.RetainedProcesses;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.launcher.adapter.execution.ExecutionPlanMapper;
import me.whereareiam.anvil.launcher.adapter.provisioning.ArtifactPlatformSource;
import me.whereareiam.anvil.launcher.adapter.provisioning.ClasspathPlatformAgentSource;
import me.whereareiam.anvil.launcher.adapter.provisioning.ProvisionedPlatformJavaSource;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.player.DefaultPlayerService;
import me.whereareiam.anvil.workbench.local.LocalWorkbench;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Assembles shared launcher services and owns them until engine shutdown or failed construction.
 */
public final class LauncherAssembly implements AutoCloseable {
	private final @NotNull LocalWorkbench workbench;
	private final @NotNull DefaultPlayerService players;
	private final @NotNull RetainedProcesses<RetainedProcessSet> retained = new RetainedProcesses<>();

	/**
	 * Shared factory used to prepare contexts while this assembly remains open.
	 */
	@Getter
	private final @NotNull ScenarioFactory scenarioFactory;
	private boolean closed;

	/**
	 * Acquires the shared services needed to prepare scenarios without starting a scenario.
	 * Failed construction releases every acquired service.
	 *
	 * @param options resolved engine options
	 * @param executions explicitly supplied execution providers
	 */
	public LauncherAssembly(@NotNull EngineOptions options, @NotNull List<ExecutionProvider> executions) {
		ProviderDiscovery discovery = new ProviderDiscovery();
		workbench = new LocalWorkbench(options, discovery.executions(executions));
		try {
			players = new DefaultPlayerService(ProtocolLibraryRegistry.discover(), options, workbench.artifacts()::obtain);
			scenarioFactory = assemble(options, discovery);
		} catch (RuntimeException | Error failure) {
			try (workbench) {
				throw failure;
			}
		}
	}

	private ScenarioFactory assemble(EngineOptions options, ProviderDiscovery discovery) {
		try {
			var locator = discovery.required(AgentArtifactLocator.class, "agent artifact locator");
			var platforms = new DefaultPlatformPlanner(options, discovery.platforms(),
					new ArtifactPlatformSource(workbench.artifacts()),
					new ClasspathPlatformAgentSource(locator, AgentExtensions.discover()),
					new ProvisionedPlatformJavaSource(workbench.java()));
			var execution = ProcessLauncher.builder()
					.options(options)
					.processes(new ManagedProcessService(workbench.executions()))
					.workspaces(workbench.workspaces())
					.platforms(platforms)
					.connections(discovery.required(AgentConnectionProvider.class, "agent connection provider"))
					.plans(new ExecutionPlanMapper(options, workbench))
					.build();

			return new DefaultScenarioFactory(workbench, options.getExecutionProviderId(), platforms, execution, players,
					retained);
		} catch (RuntimeException | Error failure) {
			try (players) {
				throw failure;
			}
		}
	}

	/**
	 * Stops the processes kept for the engine, then closes players before the workbench. Repeated calls
	 * have no effect, even after cleanup fails.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;

		closed = true;
		try (workbench; players) {
			retained.close();
		}
	}
}
