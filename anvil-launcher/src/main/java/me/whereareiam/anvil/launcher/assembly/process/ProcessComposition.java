package me.whereareiam.anvil.launcher.assembly.process;

import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import me.whereareiam.anvil.capability.process.AgentProcessCapabilityProviderAdapter;
import me.whereareiam.anvil.capability.process.ProcessCapabilityRuntime;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Validates agent-backed process capability graphs before launch, then composes each process owner after readiness.
 */
public final class ProcessComposition {
	private final @NotNull ProcessCapabilityRuntime capabilities;

	ProcessComposition(
			@NotNull PlatformPlan plan,
			@NotNull ScenarioAgentDirectory directory,
			@NotNull Collection<AgentProcessCapabilityProvider<?>> providers
	) {
		Map<String, List<CapabilityProvider<? extends ProcessCapability, CapabilityContext<ProcessCapability>>>> owners = new LinkedHashMap<>();
		plan.getProcesses().forEach((name, process) -> {
			if (!process.isAgent()) return;

			String platform = process.getDeclaration().getPlatform();
			var client = directory.register(name);
			List<CapabilityProvider<? extends ProcessCapability, CapabilityContext<ProcessCapability>>> supported = new ArrayList<>();
			for (AgentProcessCapabilityProvider<?> provider : providers)
				if (provider.supportsPlatform(platform))
					supported.add(new AgentProcessCapabilityProviderAdapter<>(provider, name, platform, new AgentRequestChannel(client)));

			owners.put(name, supported);
		});
		capabilities = new ProcessCapabilityRuntime(owners);
	}

	/**
	 * Discovers installed agent capability providers and validates each enabled process's graph.
	 * No provider capability is created and no connection is opened during this phase.
	 *
	 * @param plan validated platform declarations
	 * @param directory stable client handles used by process launches
	 * @return validated composition ready to bind after process startup
	 */
	public static @NotNull ProcessComposition discover(@NotNull PlatformPlan plan, @NotNull ScenarioAgentDirectory directory) {
		if (plan.getProcesses().values().stream().noneMatch(ProcessPlan::isAgent))
			return new ProcessComposition(plan, directory, List.of());

		ClassLoader context = Thread.currentThread().getContextClassLoader();
		List<AgentProcessCapabilityProvider<?>> providers = context == null ? List.of() : load(context);
		if (providers.isEmpty()) providers = load(ProcessComposition.class.getClassLoader());

		return new ProcessComposition(plan, directory, providers);
	}

	/**
	 * Attaches capability-owned process views after execution and agent readiness.
	 *
	 * @param processes ready process group
	 * @return group owning the composed process capabilities
	 */
	public @NotNull ProcessGroup bind(@NotNull ProcessGroup processes) {
		return capabilities.bind(processes);
	}

	private static @NotNull List<AgentProcessCapabilityProvider<?>> load(@NotNull ClassLoader loader) {
		List<AgentProcessCapabilityProvider<?>> providers = new ArrayList<>();
		for (AgentProcessCapabilityProvider<?> provider : ServiceLoader.load(AgentProcessCapabilityProvider.class, loader))
			providers.add(provider);

		return providers;
	}
}
