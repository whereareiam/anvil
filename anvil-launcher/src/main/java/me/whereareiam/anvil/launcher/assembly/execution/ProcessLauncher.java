package me.whereareiam.anvil.launcher.assembly.execution;

import lombok.Builder;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.environment.execution.api.image.ImageLocks;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionPlan;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.launcher.assembly.process.ProcessComposition;
import me.whereareiam.anvil.platform.api.PlatformPreparer;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

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

	/**
	 * Prepares platform and workspace inputs through the managed execution topology without starting JVMs.
	 *
	 * @param platformPlan validated platform requirements
	 * @param agents       stable agent directory for later generations
	 * @param capabilities optional logical process capability composition
	 * @param observer     optional public generation observer
	 * @param peers        game addresses of running processes outside the plan that its processes connect to
	 * @return prepared resource-owning group
	 */
	public @NotNull ProcessGroup prepare(
			@NotNull PlatformPlan platformPlan,
			@NotNull ScenarioAgentDirectory agents,
			@Nullable ProcessComposition capabilities,
			@Nullable ScenarioObserver observer,
			@NotNull Map<String, InetSocketAddress> peers
	) {
		var layout = workspaces.layout(options.getWorkDirectory(), platformPlan.getScenario());
		var preparation = new ScenarioProcessPreparation(
				options,
				platformPlan,
				layout,
				workspaces,
				platforms,
				connections,
				agents,
				capabilities
		);

		return processes.prepare(plan(platformPlan, layout, peers), preparation, observer);
	}

	/**
	 * Returns whether the processes of a planned scenario see a game connection's own source address.
	 *
	 * @param platformPlan planned scenario
	 * @return whether its execution provider preserves client addresses
	 */
	public boolean preservesClientAddress(@NotNull PlatformPlan platformPlan) {
		return processes.preservesClientAddress(executionProviderId(platformPlan.getScenario()));
	}

	/**
	 * Returns whether the processes of a planned scenario reach processes started for another scenario.
	 *
	 * @param platformPlan planned scenario
	 * @return whether its execution provider connects separate execution environments
	 */
	public boolean connectsEnvironments(@NotNull PlatformPlan platformPlan) {
		return processes.connectsEnvironments(executionProviderId(platformPlan.getScenario()));
	}

	private @NotNull String executionProviderId(@NotNull AnvilScenario scenario) {
		return scenario.getExecutionProviderId() == null ? options.getExecutionProviderId() : scenario.getExecutionProviderId();
	}

	private @NotNull ExecutionPlan plan(
			@NotNull PlatformPlan platforms,
			@NotNull WorkspaceLayout layout,
			@NotNull Map<String, InetSocketAddress> peers
	) {
		AnvilScenario scenario = platforms.getScenario();
		var plan = ExecutionPlan.builder()
				.executionProviderId(executionProviderId(scenario))
				.context(context(scenario))
				.processTimeouts(scenario.getProcessTimeouts().withDefaults(options.getProcessTimeouts()))
				.processScheduling(options.getProcessScheduling())
				.peers(peers);
		platforms.getProcesses().forEach((name, process) ->
				plan.process(process(name, process, layout, platforms.getProcesses().keySet())));

		return plan.build();
	}

	private @NotNull ExecutionContext context(@NotNull AnvilScenario scenario) {
		return ExecutionContext.builder()
				.cacheDirectory(options.getCacheDirectory())
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
			@NotNull WorkspaceLayout layout,
			@NotNull Set<String> planned
	) {
		MinecraftProcess declaration = plan.getDeclaration();
		var request = ProcessRequest.builder()
				.name(name)
				.workspace(layout.processDirectory(name))
				.javaSelection(plan.getJavaSelection())
				.agent(plan.isAgent())
				.publishGame(plan.isPublishGame())
				.build();

		return ProcessSpec.builder()
				.request(request)
				.proxy(plan.isProxy())
				// A dependency outside the plan is a peer that already runs.
				.dependencies(plan.getDependencies().stream().filter(planned::contains).toList())
				.readinessPattern(plan.getReadinessPattern())
				.stopCommand(plan.getStopCommand())
				.memoryMegabytes(declaration.getMemoryMegabytes())
				.build();
	}
}
