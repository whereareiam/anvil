package me.whereareiam.anvil.launcher.assembly;

import lombok.Builder;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.capability.process.ProcessCapabilityRuntime;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.launcher.adapter.execution.ExecutionPlanMapper;
import me.whereareiam.anvil.launcher.adapter.execution.ScenarioProcessPreparation;
import me.whereareiam.anvil.platform.api.PlatformPreparer;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Hands a platform plan, with its workspace layout and preparation, to managed execution.
 */
@Builder
public final class ProcessLauncher {
	private final @NotNull EngineOptions options;
	private final @NotNull ManagedProcessService processes;
	private final @NotNull WorkspaceProvisioner workspaces;
	private final @NotNull PlatformPreparer platforms;
	private final @NotNull AgentConnectionProvider connections;
	private final @NotNull ExecutionPlanMapper plans;

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
			@Nullable ProcessCapabilityRuntime capabilities,
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

		return processes.prepare(plans.map(platformPlan, layout, peers), preparation, observer);
	}

	/**
	 * Returns whether the processes of a planned scenario see a game connection's own source address.
	 *
	 * @param platformPlan planned scenario
	 * @return whether its execution provider preserves client addresses
	 */
	public boolean preservesClientAddress(@NotNull PlatformPlan platformPlan) {
		return processes.preservesClientAddress(plans.executionProviderId(platformPlan.getScenario()));
	}

	/**
	 * Returns whether the processes of a planned scenario reach processes started for another scenario.
	 *
	 * @param platformPlan planned scenario
	 * @return whether its execution provider connects separate execution environments
	 */
	public boolean connectsEnvironments(@NotNull PlatformPlan platformPlan) {
		return processes.connectsEnvironments(plans.executionProviderId(platformPlan.getScenario()));
	}
}
