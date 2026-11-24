package me.whereareiam.anvil.capability.ticks.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * How many ticks a server's process has run, counted by its agent from the moment the agent started.
 */
@Value
@Builder
@Jacksonized
public class AgentTickCount {
	/**
	 * Names the server process whose ticks are counted; a restarted server counts again from zero under a new name.
	 */
	@NotNull String run;
	/**
	 * The ticks the process has completed.
	 */
	long tick;
}
