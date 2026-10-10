package me.whereareiam.anvil.launcher.assembly.retention;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.engine.process.JoinedProcessGroup;
import me.whereareiam.anvil.engine.process.RetainedProcesses;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.planning.topology.LifetimeSplit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Connects a scenario to the processes that outlive it: planning says which they are, the engine lends a
 * running set of them, and the scenario's own processes, agents and observer are bound to that set. A
 * scenario that declares no such process holds an empty lease.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProcessLease {
	/**
	 * Plan of the processes the scenario starts itself.
	 */
	private final @NotNull PlatformPlan own;
	private final @Nullable RetainedProcessSet set;
	private final @NotNull RetainedProcesses<RetainedProcessSet> retained;

	/**
	 * Takes a lease for a planned scenario, starting the processes that outlive it when none are idle.
	 *
	 * @param scenario plan of the whole scenario
	 * @param retained the engine's kept process sets
	 * @param execution launcher that starts a new set
	 * @return the scenario's lease, which it releases when it finishes
	 * @throws ScenarioValidationException when the scenario's execution provider cannot keep processes
	 */
	public static @NotNull ProcessLease take(
			@NotNull PlatformPlan scenario,
			@NotNull RetainedProcesses<RetainedProcessSet> retained,
			@NotNull ProcessLauncher execution
	) {
		LifetimeSplit split = LifetimeSplit.of(scenario);
		if (split == null) return new ProcessLease(scenario, null, retained);
		if (!execution.connectsEnvironments(scenario))
			throw new ScenarioValidationException("Scenario '" + scenario.getScenario().getName() + "' declares processes with the engine"
					+ " lifetime, which its execution provider cannot connect to the processes of a later scenario");

		RetainedProcessSet set = retained.lease(split.identity(), () -> RetainedProcessSet.start(split.retained(), execution));

		return new ProcessLease(split.own(set.getPlan()), set, retained);
	}

	/**
	 * Returns the plan of the processes the scenario starts itself.
	 *
	 * @return the scenario's plan without the lent processes
	 */
	public @NotNull PlatformPlan ownPlan() {
		return own;
	}

	/**
	 * Returns the game addresses of the lent processes, by process name.
	 *
	 * @return addresses the scenario's own processes connect to
	 */
	public @NotNull Map<String, InetSocketAddress> addresses() {
		Map<String, InetSocketAddress> addresses = new LinkedHashMap<>();
		if (set == null) return addresses;

		for (String name : set.getPlan().getProcesses().keySet())
			addresses.put(name, set.get(name).address());

		return addresses;
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

		set.all().forEach(observer::processCreated);
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

		return new JoinedProcessGroup(own, set, set.getPlan().getProcesses().keySet(), this::release);
	}

	/**
	 * Hands the lent processes back to the engine, which keeps them only after a successful scenario.
	 *
	 * @param successful whether the scenario and the cleanup of its own processes succeeded
	 */
	public void release(boolean successful) {
		if (set != null) retained.release(set, successful);
	}
}
