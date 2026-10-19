package me.whereareiam.anvil.launcher.adapter.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.execution.api.PortReservations;
import me.whereareiam.anvil.environment.execution.api.image.ImageLocks;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionPlan;
import me.whereareiam.anvil.environment.execution.api.model.process.ContainerImage;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Translates what platform planning decided about a scenario's processes into the plan and context that
 * managed execution works from, filling in the engine's defaults.
 */
@RequiredArgsConstructor
public final class ExecutionPlanMapper {
	private final @NotNull EngineOptions options;
	private final @NotNull JavaExecutionRuntime javaRuntime;
	private final @NotNull ImageLocks imageLocks;
	private final @NotNull PortReservations ports;

	/**
	 * Returns the execution provider a scenario runs on: its own choice, or else the engine's.
	 *
	 * @param scenario scenario declaration
	 * @return execution provider identifier
	 */
	public @NotNull String executionProviderId(@NotNull AnvilScenario scenario) {
		return scenario.getExecutionProviderId() == null ? options.getExecutionProviderId() : scenario.getExecutionProviderId();
	}

	/**
	 * Translates a platform plan into the plan managed execution starts processes from.
	 *
	 * @param platforms validated platform requirements
	 * @param layout workspace locations of the scenario run
	 * @param peers game addresses of running processes outside the plan that its processes connect to
	 * @return execution plan of the planned processes
	 */
	public @NotNull ExecutionPlan map(
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
		List<String> containers = scenario.getContainers().stream().map(ContainerProcess::getName).toList();
		scenario.getContainers().forEach(container -> plan.process(container(container, layout)));
		platforms.getProcesses().forEach((name, process) ->
				plan.process(process(name, process, layout, platforms.getProcesses().keySet(), containers)));

		return plan.build();
	}

	private @NotNull ExecutionContext context(@NotNull AnvilScenario scenario) {
		return ExecutionContext.builder()
				.cacheDirectory(options.getCacheDirectory())
				.localRuntime(javaRuntime)
				.runtimeValidator(javaRuntime)
				.imageLocks(imageLocks)
				.ports(ports)
				.processPriority(options.getProcessScheduling().getPriority())
				.offline(options.isOffline())
				.refresh(options.isRefresh())
				.networkPolicy(scenario.getNetworkPolicy())
				.build();
	}

	private @NotNull ProcessSpec process(
			@NotNull String name,
			@NotNull ProcessPlan plan,
			@NotNull WorkspaceLayout layout,
			@NotNull Set<String> planned,
			@NotNull List<String> containers
	) {
		MinecraftProcess declaration = plan.getDeclaration();
		var request = ProcessRequest.builder()
				.name(name)
				.workspace(layout.processDirectory(name))
				.javaSelection(plan.getJavaSelection())
				.agent(plan.isAgent())
				.publishGame(plan.isPublishGame())
				.listeners(plan.getDeclaration().getListeners())
				.build();

		return ProcessSpec.builder()
				.request(request)
				.proxy(plan.isProxy())
				// A dependency outside the plan is a peer that already runs.
				.dependencies(plan.getDependencies().stream().filter(planned::contains).toList())
				// The scenario's containers, such as a database a plugin connects to, run before its servers and proxies.
				.dependencies(containers)
				.readinessPattern(plan.getReadinessPattern())
				.stopCommand(plan.getStopCommand())
				.memoryMegabytes(declaration.getMemoryMegabytes())
				.build();
	}

	private @NotNull ProcessSpec container(@NotNull ContainerProcess container, @NotNull WorkspaceLayout layout) {
		var request = ProcessRequest.builder()
				.name(container.getName())
				.workspace(layout.processDirectory(container.getName()))
				.image(ContainerImage.builder()
						.image(container.getImage())
						.ports(container.getPorts())
						.build())
				.build();

		return ProcessSpec.builder()
				.request(request)
				.container(true)
				.readinessPattern(container.getReadyLog() == null ? null : Pattern.compile(container.getReadyLog()))
				.memoryMegabytes(container.getMemoryMegabytes())
				.executionProviderId(container.getExecutionProviderId())
				.build();
	}
}
