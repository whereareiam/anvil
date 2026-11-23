package me.whereareiam.anvil.launcher.adapter.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionPlan;
import me.whereareiam.anvil.environment.execution.api.model.process.ContainerImage;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.workbench.api.Workbench;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
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
	private final @NotNull Workbench workbench;

	/**
	 * Returns the execution provider a scenario runs on: its own choice, or else the engine's.
	 *
	 * @param scenario scenario declaration
	 * @return execution provider identifier
	 */
	public @NotNull String executionProviderId(@NotNull AnvilScenario scenario) {
		return scenario.executionProviderId() == null ? options.executionProviderId() : scenario.executionProviderId();
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
		AnvilScenario scenario = platforms.scenario();
		var plan = ExecutionPlan.builder()
				.executionProviderId(executionProviderId(scenario))
				.context(ExecutionContext.builder()
						.processPriority(options.processScheduling().priority())
						.offline(options.offline())
						.refresh(options.refresh())
						.networkPolicy(scenario.networkPolicy())
						.build())
				.processTimeouts(scenario.processTimeouts().withDefaults(options.processTimeouts()))
				.processScheduling(options.processScheduling())
				.peers(peers);
		List<String> containers = scenario.containers().stream().map(ContainerProcess::name).toList();
		scenario.containers().forEach(container -> plan.process(container(container, layout)));
		platforms.processes().forEach((name, process) ->
				plan.process(process(name, process, layout, platforms.processes().keySet(), containers)));

		return plan.build();
	}

	private @NotNull ProcessSpec process(
			@NotNull String name,
			@NotNull ProcessPlan plan,
			@NotNull WorkspaceLayout layout,
			@NotNull Set<String> planned,
			@NotNull List<String> containers
	) {
		MinecraftProcess declaration = plan.declaration();
		var request = ProcessRequest.builder()
				.name(name)
				.workspace(layout.processDirectory(name))
				.javaSelection(plan.javaSelection())
				.agent(plan.agent())
				.publishGame(plan.publishGame())
				.listeners(plan.declaration().listeners())
				.build();

		return ProcessSpec.builder()
				.request(request)
				.consoleDirectory(consoleDirectory(request))
				.proxy(plan.proxy())
				// A dependency outside the plan is a peer that already runs.
				.dependencies(plan.dependencies().stream().filter(planned::contains).toList())
				// The scenario's containers, such as a database a plugin connects to, run before its servers and proxies.
				.dependencies(containers)
				.readinessPattern(plan.readinessPattern())
				.stopCommand(plan.stopCommand())
				.memoryMegabytes(declaration.memoryMegabytes())
				.build();
	}

	/**
	 * The engine writes a process's console log; on a workbench of another machine it goes below the engine's work
	 * directory, at the place the workspace has below the workbench's.
	 */
	private @NotNull Path consoleDirectory(@NotNull ProcessRequest request) {
		Path workspace = request.workspace();
		if (workbench.workDirectory().equals(options.workDirectory()) || !workspace.startsWith(workbench.workDirectory()))
			return workspace;

		return options.workDirectory().resolve(workbench.workDirectory().relativize(workspace).toString());
	}

	private @NotNull ProcessSpec container(@NotNull ContainerProcess container, @NotNull WorkspaceLayout layout) {
		var request = ProcessRequest.builder()
				.name(container.name())
				.workspace(layout.processDirectory(container.name()))
				.image(ContainerImage.builder()
						.image(container.image())
						.ports(container.ports())
						.build())
				.build();

		return ProcessSpec.builder()
				.request(request)
				.consoleDirectory(consoleDirectory(request))
				.container(true)
				.readinessPattern(container.readyLog() == null ? null : Pattern.compile(container.readyLog()))
				.memoryMegabytes(container.memoryMegabytes())
				.executionProviderId(container.executionProviderId())
				.build();
	}
}
