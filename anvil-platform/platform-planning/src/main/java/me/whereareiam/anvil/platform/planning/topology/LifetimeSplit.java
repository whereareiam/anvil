package me.whereareiam.anvil.platform.planning.topology;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.ProcessLifetime;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Divides a planned scenario into the processes that outlive it and the processes it starts itself.
 * The processes that outlive it are planned as a scenario of their own, so that they get their own
 * workspaces and execution environment.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LifetimeSplit {
	private final @NotNull PlatformPlan scenario;
	private final @NotNull PlatformPlan retained;

	/**
	 * Divides a plan by the declared lifetime of its processes.
	 *
	 * @param scenario plan of the whole scenario
	 * @return the division, or null when no process of the scenario outlives it
	 */
	public static @Nullable LifetimeSplit of(@NotNull PlatformPlan scenario) {
		AnvilScenario declaration = scenario.getScenario();
		List<MinecraftServer> servers = declaration.getServers().stream().filter(LifetimeSplit::retained).toList();
		List<MinecraftProxy> proxies = declaration.getProxies().stream().filter(LifetimeSplit::retained).toList();
		List<String> names = new ArrayList<>();
		servers.forEach(server -> names.add(server.getName()));
		proxies.forEach(proxy -> names.add(proxy.getName()));
		if (names.isEmpty()) return null;

		AnvilScenario own = declaration.toBuilder()
				.name("engine-" + String.join("-", names))
				.metadata(null)
				.entrypoint(names.contains(declaration.getEntrypoint()) ? declaration.getEntrypoint() : names.getFirst())
				.clearServers()
				.servers(servers)
				.clearProxies()
				.proxies(proxies)
				.setupHook(null)
				.build();
		PlatformPlan.PlatformPlanBuilder retained = PlatformPlan.builder().scenario(own);
		names.forEach(name -> retained.process(name, scenario.getProcesses().get(name)));

		return new LifetimeSplit(scenario, retained.build());
	}

	/**
	 * Returns the plan of the processes that outlive the scenario, as a scenario of their own.
	 *
	 * @return plan to start those processes from when none are running yet
	 */
	public @NotNull PlatformPlan retained() {
		return retained;
	}

	/**
	 * Returns what makes two declarations the same running processes: the process declarations with their
	 * workspaces, Java and arguments, the negotiated forwarding mode, and the scenario's execution
	 * provider, network policy and timeouts. The forwarding secret is left out, because a scenario adopts
	 * the one its processes already run with.
	 *
	 * @return value that is equal for declarations of the same running processes
	 */
	public @NotNull Object identity() {
		AnvilScenario declaration = retained.getScenario();
		List<Identity.Process> processes = retained.getProcesses().values().stream().map(Identity.Process::of).toList();

		return new Identity(declaration.getExecutionProviderId(), declaration.getNetworkPolicy(), declaration.getProcessTimeouts(), processes);
	}

	/**
	 * Returns the plan of the processes the scenario starts itself. A process that is connected to one that
	 * outlives the scenario adopts the forwarding settings that process runs with.
	 *
	 * @param running plan the running processes were started from, which may come from an earlier scenario
	 * @return the scenario's plan without the processes that outlive it
	 */
	public @NotNull PlatformPlan own(@NotNull PlatformPlan running) {
		Set<String> lent = retained.getProcesses().keySet();
		Map<ForwardingConfiguration, ForwardingConfiguration> adopted = new HashMap<>();
		running.getProcesses().forEach((name, process) ->
				adopted.put(scenario.getProcesses().get(name).getForwarding(), process.getForwarding()));

		PlatformPlan.PlatformPlanBuilder own = PlatformPlan.builder().scenario(scenario.getScenario());
		scenario.getProcesses().forEach((name, process) -> {
			if (lent.contains(name)) return;

			ForwardingConfiguration planned = process.getForwarding();
			own.process(name, process.toBuilder().forwarding(adopted.getOrDefault(planned, planned)).build());
		});

		return own.build();
	}

	private static boolean retained(MinecraftProcess process) {
		return process.getLifetime() == ProcessLifetime.ENGINE;
	}

	private record Identity(
			@Nullable String executionProviderId,
			@NotNull NetworkPolicy networkPolicy,
			@NotNull ProcessTimeouts processTimeouts,
			@NotNull List<Process> processes
	) {
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
