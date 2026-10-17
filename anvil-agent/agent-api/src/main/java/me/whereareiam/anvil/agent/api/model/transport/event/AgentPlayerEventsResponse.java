package me.whereareiam.anvil.agent.api.model.transport.event;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.agent.api.model.AgentPlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Wire response with the events an agent recorded after the requested position, oldest first.
 */
@Value
@Builder
@Jacksonized
public class AgentPlayerEventsResponse {
	/**
	 * Identifies the process generation the agent runs in. A restarted process is a new generation, whose positions
	 * start again.
	 */
	@NotNull String generation;

	/**
	 * Whether the process is a proxy, whose events name the backends it connects players to.
	 */
	boolean proxy;

	/**
	 * Whether the agent dropped events after the requested position because it keeps a bounded number.
	 */
	boolean truncated;

	/**
	 * Recorded events after the requested position, in the order the agent recorded them.
	 */
	@NotNull @Singular List<AgentPlayerEvent> events;
}
