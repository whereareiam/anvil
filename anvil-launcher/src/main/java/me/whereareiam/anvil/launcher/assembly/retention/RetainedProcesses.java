package me.whereareiam.anvil.launcher.assembly.retention;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.ProcessLifetime;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the processes that outlive their scenario for the engine and lends each running set to one
 * scenario at a time. Scenarios that declare the same processes take turns on a set; scenarios that run
 * at once each get a set of their own.
 */
@RequiredArgsConstructor
public final class RetainedProcesses implements AutoCloseable {
	private final @NotNull ProcessLauncher execution;

	private final Map<Key, Deque<RetainedProcessSet>> idle = new HashMap<>();
	private final Map<RetainedProcessSet, Key> lent = new IdentityHashMap<>();
	private boolean closed;

	/**
	 * Lends the scenario a running set of the processes it declares with the engine lifetime, starting one
	 * when none is idle. A scenario without such a process receives an empty lease.
	 *
	 * @param scenario plan of the whole scenario
	 * @return the scenario's lease, which it releases when it finishes
	 * @throws ScenarioValidationException when the scenario's execution provider cannot keep processes
	 */
	public @NotNull ProcessLease lease(@NotNull PlatformPlan scenario) {
		PlatformPlan retained = retained(scenario);
		if (retained == null) return new ProcessLease(scenario, null, this);
		if (!execution.connectsEnvironments(scenario))
			throw new ScenarioValidationException("Scenario '" + scenario.getScenario().getName() + "' declares processes with the engine"
					+ " lifetime, which its execution provider cannot connect to the processes of a later scenario");

		Key key = Key.of(retained);
		RetainedProcessSet set = idle(key);
		// Started outside the monitor, so scenarios that run at once start their sets together.
		if (set == null) set = RetainedProcessSet.start(retained, execution);

		boolean refused;
		synchronized (this) {
			refused = closed;
			if (!refused) lent.put(set, key);
		}
		if (refused) {
			set.stop(true);
			throw new IllegalStateException("Cannot keep processes for a closed engine");
		}

		return new ProcessLease(scenario, set, this);
	}

	/**
	 * Takes a lent set back. It is kept for the next scenario when the scenario that held it succeeded and
	 * left every process ready; otherwise it stops.
	 */
	void release(@NotNull RetainedProcessSet set, boolean successful) {
		boolean reusable = successful && set.ready();
		synchronized (this) {
			Key key = lent.remove(set);
			if (key == null) return;

			if (reusable && !closed) {
				idle.computeIfAbsent(key, ignored -> new ArrayDeque<>()).push(set);
				return;
			}
		}

		set.stop(reusable);
	}

	/**
	 * Stops every idle set. A set that is still lent stops when its scenario releases it.
	 */
	@Override
	public void close() {
		List<RetainedProcessSet> stopping = new ArrayList<>();
		synchronized (this) {
			if (closed) return;

			closed = true;
			idle.values().forEach(stopping::addAll);
			idle.clear();
		}

		Throwable failure = null;
		for (RetainedProcessSet set : stopping)
			try {
				set.stop(true);
			} catch (RuntimeException | Error cleanup) {
				if (failure == null) failure = cleanup;
				else if (cleanup != failure) failure.addSuppressed(cleanup);
			}
		if (failure instanceof Error error) throw error;
		if (failure != null) throw (RuntimeException) failure;
	}

	/**
	 * Takes an idle set whose processes are all ready, stopping any that died while idle.
	 */
	private @Nullable RetainedProcessSet idle(Key key) {
		while (true) {
			RetainedProcessSet set;
			synchronized (this) {
				if (closed) throw new IllegalStateException("Cannot keep processes for a closed engine");

				Deque<RetainedProcessSet> sets = idle.get(key);
				set = sets == null ? null : sets.poll();
			}
			if (set == null || set.ready()) return set;

			set.stop(false);
		}
	}

	/**
	 * Plans the processes with the engine lifetime as a scenario of their own, so that they get their own
	 * workspaces and execution environment.
	 *
	 * @return their plan, or null when the scenario declares none
	 */
	private static @Nullable PlatformPlan retained(PlatformPlan plan) {
		AnvilScenario scenario = plan.getScenario();
		List<MinecraftServer> servers = scenario.getServers().stream().filter(RetainedProcesses::retained).toList();
		List<MinecraftProxy> proxies = scenario.getProxies().stream().filter(RetainedProcesses::retained).toList();
		List<String> names = new ArrayList<>();
		servers.forEach(server -> names.add(server.getName()));
		proxies.forEach(proxy -> names.add(proxy.getName()));
		if (names.isEmpty()) return null;

		AnvilScenario own = scenario.toBuilder()
				.name("engine-" + String.join("-", names))
				.metadata(null)
				.entrypoint(names.contains(scenario.getEntrypoint()) ? scenario.getEntrypoint() : names.getFirst())
				.clearServers()
				.servers(servers)
				.clearProxies()
				.proxies(proxies)
				.setupHook(null)
				.build();
		PlatformPlan.PlatformPlanBuilder retained = PlatformPlan.builder().scenario(own);
		names.forEach(name -> retained.process(name, plan.getProcesses().get(name)));

		return retained.build();
	}

	private static boolean retained(MinecraftProcess process) {
		return process.getLifetime() == ProcessLifetime.ENGINE;
	}

	/**
	 * Everything that makes two declarations of retained processes the same running processes. The
	 * forwarding secret is left out: a scenario adopts the one its set runs with.
	 */
	private record Key(
			@Nullable String executionProviderId,
			@NotNull NetworkPolicy networkPolicy,
			@NotNull ProcessTimeouts processTimeouts,
			@NotNull List<Process> processes
	) {
		static Key of(PlatformPlan plan) {
			AnvilScenario scenario = plan.getScenario();
			List<Process> processes = plan.getProcesses().values().stream().map(Process::of).toList();

			return new Key(scenario.getExecutionProviderId(), scenario.getNetworkPolicy(), scenario.getProcessTimeouts(), processes);
		}

		private record Process(
				@NotNull MinecraftProcess declaration,
				@NotNull WorkspacePlan workspace,
				@NotNull JavaSelection javaSelection,
				@NotNull ForwardingMode forwarding,
				boolean proxyOnlineMode,
				@NotNull List<String> jvmArguments,
				@NotNull List<String> programArguments
		) {
			static Process of(ProcessPlan process) {
				return new Process(process.getDeclaration(), process.getWorkspace(), process.getJavaSelection(),
						process.getForwarding().getMode(), process.getForwarding().isProxyOnlineMode(),
						process.getJvmArguments(), process.getProgramArguments());
			}
		}
	}
}
