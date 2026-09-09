package me.whereareiam.anvil.launcher.assembly;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.AgentArtifactLocator;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.engine.EngineExtension;
import me.whereareiam.anvil.api.engine.EngineRegistration;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.launcher.assembly.execution.CacheImageLocks;
import me.whereareiam.anvil.launcher.assembly.execution.JavaExecutionRuntime;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.launcher.assembly.provisioning.ArtifactPlatformSource;
import me.whereareiam.anvil.launcher.assembly.provisioning.ProvisioningServices;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProviderRegistry;
import me.whereareiam.anvil.protocol.player.DefaultPlayerService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Installs the launcher's scoped services and transfers their lifetime to the engine.
 */
@RequiredArgsConstructor
public final class LauncherEngineExtension implements EngineExtension {
	private final @NotNull EngineOptions options;
	private final @NotNull List<ExecutionProvider> executions;

	@Override
	public void install(@NotNull EngineRegistration registration) {
		ProviderDiscovery discovery = new ProviderDiscovery();
		ProvisioningServices provisioning = new ProvisioningServices(options);
		try {
			registration.own(provisioning);
		} catch (RuntimeException | Error failure) {
			try (provisioning) {
				throw failure;
			}
		}

		var protocol = ProtocolProviderRegistry.discover().select(options.getProtocolId());
		var players = new DefaultPlayerService(protocol, options.getCacheDirectory(), provisioning.getArtifacts()::obtain);
		registration.own(players);

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

		registration.executor(new ScenarioLauncher(platforms, execution, players, protocol.id()));
	}
}
