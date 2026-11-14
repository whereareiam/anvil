package me.whereareiam.anvil.launcher.assembly;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.capability.process.ProcessCapabilityRuntime;
import me.whereareiam.anvil.launcher.adapter.process.ScenarioPlatforms;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Assembles the processes that outlive their scenario into a running process group of their own and
 * carries what a scenario needs beside the processes: the plan they were started from and their agents. Their
 * capabilities serve every scenario the set is lent to and hear of each scenario's start and end from its lease.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class RetainedProcessSet implements ProcessGroup {
	/**
	 * Plan the set was started from; its forwarding settings are the ones every scenario using the set adopts.
	 */
	@Getter
	private final @NotNull PlatformPlan plan;
	@Delegate
	private final @NotNull ProcessGroup processes;
	@Getter
	private final @NotNull ScenarioAgentDirectory agents;
	private final @NotNull ProcessCapabilityRuntime capabilities;

	/**
	 * Prepares and starts the planned processes. A failed start releases everything it acquired.
	 *
	 * @param plan processes of the set, planned as a scenario of their own
	 * @param execution launcher that prepares their group
	 * @return running set
	 */
	static @NotNull RetainedProcessSet start(@NotNull PlatformPlan plan, @NotNull ProcessLauncher execution) {
		var agents = new ScenarioAgentDirectory();
		var capabilities = ProcessComposition.discover(plan, agents, new ScenarioPlatforms(agents));
		ProcessGroup processes = execution.prepare(plan, agents, capabilities, null, Map.of());
		try {
			processes = capabilities.bindRetained(processes);
			processes.startAll();

			return new RetainedProcessSet(plan, processes, agents, capabilities);
		} catch (RuntimeException | Error failure) {
			try {
				processes.finish(false);
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	/**
	 * Tells the set's capabilities that a scenario took the set.
	 */
	void startScenario() {
		capabilities.startScenario();
	}

	/**
	 * Tells the set's capabilities that the scenario it was lent to finished.
	 *
	 * @param passed whether the scenario passed
	 */
	void finishScenario(boolean passed) {
		capabilities.finishScenario(passed);
	}
}
