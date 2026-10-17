package me.whereareiam.anvil.launcher.assembly;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.capability.process.ProcessCapabilityRuntime;
import me.whereareiam.anvil.launcher.adapter.process.AgentRequestChannel;
import me.whereareiam.anvil.launcher.adapter.process.ScenarioPlatforms;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hands the planned processes that run an agent, and the request channels of those agents, to process
 * capability discovery.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProcessComposition {
	/**
	 * Discovers the process capabilities of a plan. No capability is created and no connection is opened.
	 *
	 * @param plan validated platform declarations
	 * @param directory stable client handles used by process launches
	 * @param platforms the platforms of the scenario's processes, read through their agents
	 * @return validated capability runtime, ready to bind after process startup
	 */
	public static @NotNull ProcessCapabilityRuntime discover(
			@NotNull PlatformPlan plan,
			@NotNull ScenarioAgentDirectory directory,
			@NotNull ScenarioPlatforms platforms
	) {
		Map<String, String> declared = new LinkedHashMap<>();
		plan.getProcesses().forEach((name, process) -> {
			if (process.isAgent()) declared.put(name, process.getDeclaration().getPlatform());
		});

		return ProcessCapabilityRuntime.discover(declared, name -> new AgentRequestChannel(directory.register(name)), platforms::platform);
	}
}
