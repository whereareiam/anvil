package me.whereareiam.anvil.workbench.requirement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.exception.scenario.ScenarioRequirementsException;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioRequirements;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks what a scenario requires, declared and implied, against the facts of the workbench chosen to run it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequirementCheck {
	private static final String NEOFORGE = "neoforge";

	/**
	 * Refuses a scenario the workbench cannot run, naming every unmet requirement at once.
	 *
	 * @param scenario scenario about to be prepared
	 * @param defaultExecutionProviderId execution provider of processes that name none, the engine's default
	 * @param facts current facts of the chosen workbench
	 * @throws ScenarioRequirementsException when at least one requirement is unmet
	 */
	public static void require(
			@NotNull AnvilScenario scenario,
			@NotNull String defaultExecutionProviderId,
			@NotNull WorkbenchFacts facts
	) {
		List<String> unmet = unmet(scenario, defaultExecutionProviderId, facts);
		if (!unmet.isEmpty())
			throw new ScenarioRequirementsException(scenario.getName(), facts.getWorkbenchName(), unmet);
	}

	/**
	 * Lists every requirement of a scenario that the workbench does not meet, in declaration order.
	 *
	 * @param scenario scenario about to be prepared
	 * @param defaultExecutionProviderId execution provider of processes that name none, the engine's default
	 * @param facts current facts of the chosen workbench
	 * @return unmet requirements, one sentence each; empty when the workbench can run the scenario
	 */
	public static @NotNull List<String> unmet(
			@NotNull AnvilScenario scenario,
			@NotNull String defaultExecutionProviderId,
			@NotNull WorkbenchFacts facts
	) {
		List<String> unmet = new ArrayList<>();
		for (String execution : executions(scenario, defaultExecutionProviderId)) {
			String reason = facts.getUnavailableExecutions().get(execution);
			if (reason != null) unmet.add("execution provider '" + execution + "' is unavailable: " + reason);
		}

		if (!facts.isLocal()) unmet.addAll(localOnly(scenario));

		ScenarioRequirements requirements = scenario.getRequirements();
		for (String host : requirements.getReaches())
			if (facts.getReachableHosts().stream().noneMatch(pattern -> HostPattern.matches(pattern, host)))
				unmet.add("its processes may not reach " + host);

		Integer processors = requirements.getProcessors();
		if (processors != null && processors > facts.getProcessors())
			unmet.add("it needs " + processors + " processors, but " + facts.getProcessors() + " are available");

		Integer memory = requirements.getMemoryMegabytes();
		if (memory != null && memory > facts.getMemoryMegabytes())
			unmet.add("it needs " + memory + " MiB of memory, but " + facts.getMemoryMegabytes() + " MiB are available");

		return List.copyOf(unmet);
	}

	/**
	 * What only a workbench on the engine's own machine can do: link NeoForge's installation with the machine's own
	 * file operations, and run a Java installation the declaration names by its path on the engine's machine.
	 */
	private static List<String> localOnly(AnvilScenario scenario) {
		List<String> unmet = new ArrayList<>();
		List<MinecraftProcess> processes = new ArrayList<>(scenario.getServers());
		processes.addAll(scenario.getProxies());
		for (MinecraftProcess process : processes) {
			if (process.getPlatform().equals(NEOFORGE))
				unmet.add("NeoForge process '" + process.getName() + "' runs only on the workbench in the engine's own JVM");
			if (localJava(process.getJavaSelection()) || localJava(scenario.getJavaSelection()))
				unmet.add("process '" + process.getName() + "' names a Java installation by its path on the engine's machine");
		}
		return unmet;
	}

	private static boolean localJava(JavaSelection selection) {
		return selection.getSource() instanceof LocalJavaHome || selection.getSource() instanceof LocalJavaExecutable;
	}

	/**
	 * Execution providers the scenario's processes run on. Servers and proxies run on the scenario's provider, a
	 * container on its own when it names one; a scenario of remotes alone runs nothing.
	 */
	private static Set<String> executions(AnvilScenario scenario, String defaultExecutionProviderId) {
		String scenarioExecution = scenario.getExecutionProviderId() == null
				? defaultExecutionProviderId : scenario.getExecutionProviderId();

		Set<String> executions = new LinkedHashSet<>();
		if (!scenario.getServers().isEmpty() || !scenario.getProxies().isEmpty()) executions.add(scenarioExecution);
		for (ContainerProcess container : scenario.getContainers())
			executions.add(container.getExecutionProviderId() == null ? scenarioExecution : container.getExecutionProviderId());

		return executions;
	}
}
