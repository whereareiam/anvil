package me.whereareiam.anvil.agent.api.transport;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * Scenario-owned agent connections made available to host-side capability providers.
 * Connections are borrowed; the scenario closes them after player cleanup.
 */
public interface AgentDirectory {
	/**
	 * Returns an immutable snapshot keyed by scenario process name.
	 *
	 * @return authenticated agent connections
	 */
	@NotNull Map<String, AgentClient> agents();

	/**
	 * Looks up the agent installed into one process.
	 *
	 * @param process scenario process name
	 * @return matching agent when the process supplies one
	 */
	default @NotNull Optional<AgentClient> find(@NotNull String process) {
		return Optional.ofNullable(agents().get(process));
	}

	/**
	 * Resolves the agent installed into one process.
	 *
	 * @param process scenario process name
	 * @return authenticated connection
	 * @throws AgentException when no agent is available for that process
	 */
	default @NotNull AgentClient require(@NotNull String process) {
		return find(process).orElseThrow(() -> new AgentException("No agent for process '" + process
				+ "'. Available: " + agents().keySet()));
	}
}
