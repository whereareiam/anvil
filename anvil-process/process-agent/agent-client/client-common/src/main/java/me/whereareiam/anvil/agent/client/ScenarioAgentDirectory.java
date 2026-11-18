package me.whereareiam.anvil.agent.client;

import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers stable agent handles by process name for one scenario.
 */
public final class ScenarioAgentDirectory implements AgentDirectory {
	private final Map<String, ProcessAgentClient> agents = new ConcurrentHashMap<>();

	/**
	 * Returns the existing handle or creates an unavailable handle ready for its first session.
	 *
	 * @param process scenario process name
	 * @return stable handle used by every generation of the process
	 */
	public @NotNull ProcessAgentClient register(@NotNull String process) {
		return agents.computeIfAbsent(process, ignored -> new ProcessAgentClient());
	}

	@Override
	public @NotNull Map<String, AgentClient> agents() {
		return Map.copyOf(agents);
	}
}
