package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The world changes of one agent's log that a host has not read yet.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldChanges {
	/**
	 * The identity of the agent's log, which a new server process starts afresh.
	 */
	@NotNull String log;
	/**
	 * The changes after the requested position, oldest first.
	 */
	@NotNull @Singular List<AgentWorldChange> changes;
}
