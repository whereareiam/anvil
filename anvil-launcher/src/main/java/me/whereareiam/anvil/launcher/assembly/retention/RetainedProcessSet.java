package me.whereareiam.anvil.launcher.assembly.retention;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.launcher.assembly.process.ProcessComposition;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Assembles the processes that outlive their scenario into a running process group of their own and
 * carries what a scenario needs beside the processes: the plan they were started from and their agents.
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

	/**
	 * Prepares and starts the planned processes. A failed start releases everything it acquired.
	 *
	 * @param plan processes of the set, planned as a scenario of their own
	 * @param execution launcher that prepares their group
	 * @return running set
	 */
	static @NotNull RetainedProcessSet start(@NotNull PlatformPlan plan, @NotNull ProcessLauncher execution) {
		var agents = new ScenarioAgentDirectory();
		var capabilities = ProcessComposition.discover(plan, agents);
		ProcessGroup processes = execution.prepare(plan, agents, capabilities, null, Map.of());
		try {
			processes = capabilities.bind(processes);
			processes.startAll();

			return new RetainedProcessSet(plan, processes, agents);
		} catch (RuntimeException | Error failure) {
			try {
				processes.finish(false);
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}
}
