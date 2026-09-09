package me.whereareiam.anvil.launcher.assembly.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.EngineOptions;
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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Binds complete execution topology to scoped workspace and platform preparation.
 */
@RequiredArgsConstructor
final class ScenarioProcessPreparation implements ExecutionPreparation {
	private final @NotNull EngineOptions options;
	private final @NotNull PlatformPlan plan;
	private final @NotNull WorkspaceLayout layout;
	private final @NotNull WorkspaceProvisioner workspaces;
	private final @NotNull PlatformPreparer platforms;
	private final @NotNull AgentConnectionProvider connections;
	private final @NotNull ScenarioAgentDirectory agents;

	@Override
	public void open() {
		workspaces.recreateRunDirectory(options.getWorkDirectory(), layout.getRunDirectory());
	}

	@Override
	public @NotNull PreparedProcess prepare(
			@NotNull ProcessSpec process,
			@NotNull ProcessTarget target,
			@NotNull Map<String, InetSocketAddress> peers
	) {
		var planned = plan.getProcesses().get(process.getRequest().getName());
		var declaration = planned.getDeclaration();
		var request = PlatformRequest.builder()
				.scenario(plan.getScenario())
				.workDirectory(process.getRequest().getWorkspace())
				.workspaceGroupDirectory(layout.getRunDirectory())
				.bindAddress(target.bindAddress())
				.port(target.peerAddress().getPort())
				.processAddresses(peers)
				.build();
		var workspace = workspaces.prepare(WorkspaceRequest.builder()
				.root(options.getWorkDirectory())
				.directory(request.getWorkDirectory())
				.process(declaration)
				.plan(planned.getWorkspace())
				.providerDefaults(planned.getDefaultCaches())
				.build());
		try {
			var jar = platforms.resolve(planned, request);
			var command = JavaCommand.builder()
					.jar(jar)
					.memoryMegabytes(declaration.getMemoryMegabytes())
					.jvmArguments(declaration.getJvmArguments())
					.arguments(planned.getProgramArguments())
					.build();
			return new PreparedPlatformProcess(
					planned,
					request,
					platforms,
					workspace,
					command,
					target,
					connections,
					planned.isAgent() ? agents.register(declaration.getName()) : null
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

	@Override
	public void finish(boolean successful) {
		workspaces.finishRunDirectory(options.getWorkDirectory(), layout.getRunDirectory(),
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
