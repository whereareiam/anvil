package me.whereareiam.anvil.capability.ticks.agent;

import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickPace;
import org.jetbrains.annotations.NotNull;

/**
 * One platform's control of how fast its server runs its ticks, which its platform side implements over the
 * platform's own API. The operations call it on the thread the platform requires, through
 * {@link me.whereareiam.anvil.agent.server.api.PlatformAgent#call}.
 */
public interface ServerTicks {
	/**
	 * Tells how fast the server runs its ticks now.
	 *
	 * @return the pace
	 */
	@NotNull AgentTickPace pace();

	/**
	 * Has the server run its ticks at a pace: at its rate, or sprinting until another pace is set.
	 *
	 * @param pace the pace
	 */
	void pace(@NotNull AgentTickPace pace);
}
