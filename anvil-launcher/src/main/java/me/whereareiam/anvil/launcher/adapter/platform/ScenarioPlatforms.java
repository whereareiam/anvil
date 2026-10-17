package me.whereareiam.anvil.launcher.adapter.platform;

import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.capability.api.platform.ProcessPlatform;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The platforms of one scenario's processes, each read through its agent and shared by every capability of that
 * process. Processes that already ran when the scenario began, such as those kept for the engine's lifetime, report
 * only what happens from then on.
 */
public final class ScenarioPlatforms {
	private final AgentDirectory agents;
	private final Map<String, AgentProcessPlatform> platforms = new ConcurrentHashMap<>();
	private final Instant began = Instant.now();

	/**
	 * Binds the platforms to a scenario's agents. What a process reported before this moment is left out.
	 *
	 * @param agents every agent the scenario reaches, its own processes' and the retained ones'
	 */
	public ScenarioPlatforms(@NotNull AgentDirectory agents) {
		this.agents = agents;
	}

	/**
	 * Returns the platform of one process.
	 *
	 * @param process scenario process name
	 * @return the process's platform
	 */
	public @NotNull ProcessPlatform platform(@NotNull String process) {
		return of(process);
	}

	private AgentProcessPlatform of(String process) {
		return platforms.computeIfAbsent(process, name -> new AgentProcessPlatform(name, () -> agents.find(name), began));
	}
}
