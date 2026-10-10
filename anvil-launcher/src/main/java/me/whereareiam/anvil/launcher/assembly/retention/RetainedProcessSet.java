package me.whereareiam.anvil.launcher.assembly.retention;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.launcher.assembly.process.ProcessComposition;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns one running set of the processes that outlive their scenario, with its agents and capabilities.
 * The set is a process group of its own, so it has its own workspaces and execution environment.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class RetainedProcessSet {
	/**
	 * Plan the set was started from; its forwarding settings are the ones every scenario using the set adopts.
	 */
	@Getter
	private final @NotNull PlatformPlan plan;
	@Getter
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

	/**
	 * Returns whether every process of the set is ready to serve another scenario.
	 */
	boolean ready() {
		Collection<RunningProcess> running = processes.all();
		return running.size() == plan.getProcesses().size()
				&& running.stream().allMatch(process -> process.state() == ProcessState.READY);
	}

	/**
	 * Returns the game addresses a scenario's own processes connect to, by process name.
	 */
	@NotNull Map<String, InetSocketAddress> addresses() {
		Map<String, InetSocketAddress> addresses = new LinkedHashMap<>();
		for (String name : plan.getProcesses().keySet())
			addresses.put(name, processes.get(name).address());

		return addresses;
	}

	/**
	 * Stops the processes and finalizes their workspaces.
	 *
	 * @param successful false keeps the workspaces for diagnosis under the engine's retention policy
	 */
	void stop(boolean successful) {
		processes.finish(successful);
	}
}
