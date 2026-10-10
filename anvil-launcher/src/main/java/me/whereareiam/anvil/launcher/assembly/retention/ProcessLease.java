package me.whereareiam.anvil.launcher.assembly.retention;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A scenario's hold on the processes that outlive it: the running set it was lent, or nothing when the
 * scenario declares no such process. The scenario prepares its own processes against the lease and hands
 * the set back when it finishes.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ProcessLease {
	private final @NotNull PlatformPlan scenario;
	private final @Nullable RetainedProcessSet set;
	private final @NotNull RetainedProcesses owner;

	/**
	 * Returns the plan of the processes the scenario starts itself. A process that is connected to a lent
	 * process adopts the forwarding settings that process already runs with.
	 *
	 * @return the scenario's plan without the lent processes
	 */
	public @NotNull PlatformPlan ownPlan() {
		if (set == null) return scenario;

		Set<String> lent = set.getPlan().getProcesses().keySet();
		Map<ForwardingConfiguration, ForwardingConfiguration> running = new HashMap<>();
		set.getPlan().getProcesses().forEach((name, process) ->
				running.put(scenario.getProcesses().get(name).getForwarding(), process.getForwarding()));

		PlatformPlan.PlatformPlanBuilder own = PlatformPlan.builder().scenario(scenario.getScenario());
		scenario.getProcesses().forEach((name, process) -> {
			if (lent.contains(name)) return;

			ForwardingConfiguration planned = process.getForwarding();
			own.process(name, process.toBuilder().forwarding(running.getOrDefault(planned, planned)).build());
		});

		return own.build();
	}

	/**
	 * Returns the game addresses of the lent processes, by process name.
	 *
	 * @return addresses the scenario's own processes connect to
	 */
	public @NotNull Map<String, InetSocketAddress> addresses() {
		return set == null ? Map.of() : set.addresses();
	}

	/**
	 * Returns a directory of the scenario's own agents and those of the lent processes.
	 *
	 * @param own agents of the processes the scenario starts itself
	 * @return every agent the scenario's players and capabilities can reach
	 */
	public @NotNull AgentDirectory agents(@NotNull AgentDirectory own) {
		if (set == null) return own;

		AgentDirectory lent = set.getAgents();
		return () -> {
			Map<String, AgentClient> agents = new LinkedHashMap<>(lent.agents());
			agents.putAll(own.agents());

			return Map.copyOf(agents);
		};
	}

	/**
	 * Reports the lent processes to the scenario's observer, in the state they are in.
	 *
	 * @param observer optional observer of the scenario's process generations
	 */
	public void announce(@Nullable ScenarioObserver observer) {
		if (set == null || observer == null) return;

		set.getProcesses().all().forEach(observer::processCreated);
	}

	/**
	 * Joins the lent processes with the scenario's own into the group the scenario owns. Finishing that
	 * group hands the lent processes back.
	 *
	 * @param own prepared group of the processes the scenario starts itself
	 * @return group exposing every process of the scenario
	 */
	public @NotNull ProcessGroup join(@NotNull ProcessGroup own) {
		if (set == null) return own;

		return new JoinedProcessGroup(own, set.getProcesses(), set.getPlan().getProcesses().keySet(), this::release);
	}

	/**
	 * Hands the lent processes back. They serve another scenario only after a successful one; otherwise
	 * they stop and their workspaces are kept for diagnosis.
	 *
	 * @param successful whether the scenario and the cleanup of its own processes succeeded
	 */
	public void release(boolean successful) {
		if (set != null) owner.release(set, successful);
	}
}
