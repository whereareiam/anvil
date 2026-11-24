package me.whereareiam.anvil.capability.ticks.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * How fast a server runs its ticks, in the two settings Minecraft's tick rate manager keeps: its rate, and whether it
 * sprints, running each tick as soon as the previous one is done whatever its rate.
 */
@Value
@Builder
@Jacksonized
public class AgentTickPace {
	/**
	 * The ticks a second the server runs while it does not sprint.
	 */
	float rate;
	/**
	 * Whether the server sprints.
	 */
	boolean sprinting;
}
