package me.whereareiam.anvil.launcher.adapter.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.model.process.ProcessAddresses;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.capability.process.ProcessCapabilityRuntime;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.preparation.ExecutionPreparation;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedProcess;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRequest;
import me.whereareiam.anvil.platform.api.PlatformPreparer;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.PlatformRequest;
import me.whereareiam.anvil.workbench.api.Workbench;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Binds complete execution topology to scoped workspace and platform preparation.
 */
@RequiredArgsConstructor
public final class ScenarioProcessPreparation implements ExecutionPreparation {
	private final @NotNull EngineOptions options;
	private final @NotNull Workbench workbench;
	private final @NotNull PlatformPlan plan;
	private final @NotNull WorkspaceLayout layout;
	private final @NotNull WorkspaceProvisioner workspaces;
	private final @NotNull PlatformPreparer platforms;
	private final @NotNull AgentConnectionProvider connections;
	private final @NotNull ScenarioAgentDirectory agents;
	private final @Nullable ProcessCapabilityRuntime capabilities;

	@Override
	public void open() {
		workspaces.recreateRunDirectory(workbench.workDirectory(), layout.getRunDirectory());
	}

	@Override
	public @NotNull PreparedProcess prepare(
			@NotNull ProcessSpec process,
			@NotNull ProcessTarget target,
			@NotNull Map<String, InetSocketAddress> peers,
			@NotNull ProcessAddresses processes
	) {
		ProcessFiles files = workspaces.files(process.getRequest().getWorkspace());
		if (process.isContainer())
			return PreparedContainer.prepare(container(process.getRequest().getName()), process, files, processes);

		var planned = plan.getProcesses().get(process.getRequest().getName());
		var declaration = planned.getDeclaration();
		UnaryOperator<String> addresses = processes::resolve;
		var request = PlatformRequest.builder()
				.scenario(plan.getScenario())
				.workDirectory(process.getRequest().getWorkspace())
				.files(files)
				.cacheDirectory(workbench.cacheDirectory())
				.workspaceGroupDirectory(layout.getRunDirectory())
				.bindAddress(target.bindAddress())
				.port(target.peerAddress().getPort())
				.processAddresses(peers)
				.build();
		var workspace = workspaces.prepare(WorkspaceRequest.builder()
				.root(workbench.workDirectory())
				.directory(request.getWorkDirectory())
				.process(declaration)
				.plan(stored(planned.getWorkspace()))
				.providerDefaults(planned.getDefaultCaches())
				.text(addresses)
				.build());
		try {
			var jar = platforms.resolve(planned, request);
			var command = JavaCommand.builder()
					.jar(jar)
					.memoryMegabytes(declaration.getMemoryMegabytes())
					.jvmArguments(planned.getJvmArguments().stream().map(addresses).toList())
					.arguments(planned.getProgramArguments().stream().map(addresses).toList())
					.build();

			return new PreparedPlatformProcess(
					planned,
					request,
					platforms,
					workspace,
					command,
					target,
					connections,
					planned.isAgent() ? agents.register(declaration.getName()) : null,
					capabilities == null ? null : () -> capabilities.initialize(declaration.getName()),
					capabilities == null ? null : capabilities.owner(declaration.getName())
			);
		} catch (IOException failure) {
			ProvisioningException contextual = new ProvisioningException("Could not prepare process '" + declaration.getName() + "'", failure);
			rollback(workspace, contextual);
			throw contextual;
		} catch (RuntimeException | Error failure) {
			rollback(workspace, failure);
			throw failure;
		}
	}

	/**
	 * Hands the files a workspace copies from the engine's machine, such as plugin JARs and the platform agent, to
	 * the workbench, and points the assets at the workbench's copies.
	 */
	private WorkspacePlan stored(WorkspacePlan plan) {
		List<WorkspaceAsset> assets = plan.getAssets().stream()
				.map(asset -> asset.getSource().isPath()
						? asset.toBuilder().source(AssetSource.path(workbench.store(asset.getSource().getPath()))).build()
						: asset)
				.toList();

		return plan.toBuilder().clearAssets().assets(assets).build();
	}

	private ContainerProcess container(String name) {
		return plan.getScenario().getContainers().stream()
				.filter(container -> container.getName().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("The scenario declares no container '" + name + "'"));
	}

	@Override
	public void finish(boolean successful) {
		workspaces.finishRunDirectory(workbench.workDirectory(), layout.getRunDirectory(),
				successful, options.isKeepFailedWorkspaces());
	}

	private void rollback(@NotNull PreparedWorkspace workspace, @NotNull Throwable failure) {
		try {
			workspace.finish(false);
		} catch (RuntimeException | Error cleanup) {
			if (cleanup != failure) failure.addSuppressed(cleanup);
		}
	}
}
