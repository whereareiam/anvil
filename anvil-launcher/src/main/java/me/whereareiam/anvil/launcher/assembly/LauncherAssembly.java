package me.whereareiam.anvil.launcher.assembly;

import lombok.Getter;
import me.whereareiam.anvil.agent.client.api.AgentArtifactLocator;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioFactory;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.launcher.assembly.execution.CacheImageLocks;
import me.whereareiam.anvil.launcher.assembly.execution.JavaExecutionRuntime;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.launcher.assembly.provisioning.ArtifactPlatformSource;
import me.whereareiam.anvil.launcher.assembly.provisioning.ProvisioningServices;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.player.DefaultPlayerService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Assembles shared launcher services and owns them until engine shutdown or failed construction.
 */
public final class LauncherAssembly implements AutoCloseable {
	private final @NotNull ProvisioningServices provisioning;
	private final @NotNull DefaultPlayerService players;

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
		provisioning = new ProvisioningServices(options);
		try {
			players = new DefaultPlayerService(ProtocolLibraryRegistry.discover(), options, provisioning.getArtifacts()::obtain);
			scenarioFactory = assemble(options, executions, discovery);
		} catch (RuntimeException | Error failure) {
			try (provisioning) {
				throw failure;
			}
		}
	}

	private ScenarioFactory assemble(
			EngineOptions options,
			List<ExecutionProvider> executions,
			ProviderDiscovery discovery
	) {
		try {
			var locator = discovery.required(AgentArtifactLocator.class, "agent artifact locator");
			var platforms = new DefaultPlatformPlanner(options, discovery.platforms(),
					new ArtifactPlatformSource(provisioning.getArtifacts()),
					descriptor -> locator.locate(descriptor.getEntrypointClassName()));
			var execution = ProcessLauncher.builder()
					.options(options)
					.processes(new ManagedProcessService(discovery.executions(executions)))
					.workspaces(provisioning.getWorkspaces())
					.platforms(platforms)
					.connections(discovery.required(AgentConnectionProvider.class, "agent connection provider"))
					.javaRuntime(new JavaExecutionRuntime(provisioning.getJava()))
					.imageLocks(new CacheImageLocks(provisioning.getCache()))
					.build();

			return new DefaultScenarioFactory(platforms, execution, players);
		} catch (RuntimeException | Error failure) {
			try (players) {
				throw failure;
			}
		}
	}

	/**
	 * Closes players before provisioning. Repeated calls have no effect, even after cleanup fails.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;

		closed = true;
		try (provisioning) {
			players.close();
		}
	}
}
