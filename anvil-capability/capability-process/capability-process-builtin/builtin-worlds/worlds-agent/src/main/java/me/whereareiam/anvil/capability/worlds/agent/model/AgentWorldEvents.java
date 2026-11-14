package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The world events of one agent's log that a host has not read yet.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldEvents {
	/**
	 * The identity of the agent's log, which a new server process starts afresh.
	 */
	@NotNull String log;
	/**
	 * The events after the requested position, oldest first.
	 */
	@NotNull @Singular List<AgentWorldEvent> events;
}
