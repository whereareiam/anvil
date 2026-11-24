package me.whereareiam.anvil.capability.ticks.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Asks the agent to answer once its server has completed a tick after the one the host saw last, or once a time has
 * passed without one, as when the server is frozen or a tick takes long.
 */
@Value
@Builder
@Jacksonized
public class AgentTickWait {
	/**
	 * The process the host saw last. A server that has restarted since answers at once with its new process's count.
	 */
	@NotNull String run;
	/**
	 * The tick the host saw last.
	 */
	long after;
	/**
	 * How long the agent waits for the next tick at most, in milliseconds; the agent answers sooner than its client's
	 * read timeout whatever this asks.
	 */
	long waitMillis;
}
