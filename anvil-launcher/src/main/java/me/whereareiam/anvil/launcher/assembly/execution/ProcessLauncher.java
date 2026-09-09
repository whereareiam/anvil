package me.whereareiam.anvil.launcher.assembly.execution;

import lombok.Builder;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.environment.execution.api.image.ImageLocks;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionPlan;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.platform.api.PlatformPreparer;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import org.jetbrains.annotations.NotNull;

/**
 * Launches resolved platform processes through execution-owned plans and workspace preparation.
 */
@Builder
public final class ProcessLauncher {
	private final @NotNull EngineOptions options;
	private final @NotNull ManagedProcessService processes;
	private final @NotNull WorkspaceProvisioner workspaces;
	private final @NotNull PlatformPreparer platforms;
	private final @NotNull AgentConnectionProvider connections;
	private final @NotNull JavaExecutionRuntime javaRuntime;
	private final @NotNull ImageLocks imageLocks;

	public @NotNull ProcessGroup start(@NotNull PlatformPlan platformPlan, @NotNull ScenarioAgentDirectory agents) {
		var layout = workspaces.layout(options.getWorkDirectory(), platformPlan.getScenario());
		var preparation = new ScenarioProcessPreparation(
				options,
				platformPlan,
				layout,
				workspaces,
				platforms,
				connections,
				agents
		);

		return processes.start(plan(platformPlan, layout), preparation);
	}

	private @NotNull ExecutionPlan plan(@NotNull PlatformPlan platforms, @NotNull WorkspaceLayout layout) {
		AnvilScenario scenario = platforms.getScenario();
		var plan = ExecutionPlan.builder()
				.executionId(scenario.getExecution() == null ? options.getExecutionId() : scenario.getExecution())
				.context(context(scenario))
				.startupTimeout(scenario.getStartupTimeout())
				.stopTimeout(options.getStopTimeout())
				.parallelism(options.getParallelism())
				.startupMemoryMegabytes(options.getStartupMemoryMegabytes());
		platforms.getProcesses().forEach((name, process) ->
				plan.process(process(name, process, layout)));

		return plan.build();
	}

	private @NotNull ExecutionContext context(@NotNull AnvilScenario scenario) {
		return ExecutionContext.builder()
				.cacheDirectory(options.getCacheDirectory())
				.bindAddress(scenario.getBindAddress())
				.localRuntime(javaRuntime)
				.runtimeValidator(javaRuntime)
				.imageLocks(imageLocks)
				.offline(options.isOffline())
				.refresh(options.isRefresh())
				.networkPolicy(scenario.getNetworkPolicy())
				.build();
	}

	private @NotNull ProcessSpec process(
			@NotNull String name,
			@NotNull ProcessPlan plan,
			@NotNull WorkspaceLayout layout
	) {
		MinecraftProcess declaration = plan.getDeclaration();
		var request = ProcessRequest.builder()
				.name(name)
				.workspace(layout.processDirectory(name))
				.javaRequirement(plan.getJavaRequirement())
				.javaSource(plan.getJavaSource())
				.minimumJavaVersion(plan.getMinimumJavaVersion())
				.agent(plan.isAgent())
				.publishGame(plan.isPublishGame())
				.build();

		return ProcessSpec.builder()
				.request(request)
				.proxy(plan.isProxy())
				.dependencies(plan.getDependencies())
				.readinessPattern(plan.getReadinessPattern())
				.stopCommand(plan.getStopCommand())
				.memoryMegabytes(declaration.getMemoryMegabytes())
				.build();
	}
}
