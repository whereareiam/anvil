package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

/**
 * Asks for the world changes a server reported after the ones its host has already read.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldChangesRequest {
	/**
	 * The log the host read last, or {@code null} before its first read. A log that is no longer the agent's, because
	 * the server restarted, is read again from its start.
	 */
	@Nullable String log;
	/**
	 * The position of the last change the host read from that log, or {@code 0} for none.
	 */
	long after;
}
