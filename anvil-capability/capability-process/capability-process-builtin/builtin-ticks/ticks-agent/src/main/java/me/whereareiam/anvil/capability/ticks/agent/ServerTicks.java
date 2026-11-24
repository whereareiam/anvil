package me.whereareiam.anvil.capability.ticks.agent;

/**
 * One platform's control of its server's tick rate, which its platform side implements over the platform's own API.
 * The operations call it on the thread the platform requires, through
 * {@link me.whereareiam.anvil.agent.server.api.PlatformAgent#call}.
 */
public interface ServerTicks {
	/**
	 * Has the server run a number of ticks as fast as it can, replacing a sprint already running.
	 *
	 * @param ticks positive number of ticks to sprint
	 */
	void sprint(int ticks);

	/**
	 * Stops a sprint.
	 *
	 * @return whether a sprint was running
	 */
	boolean stopSprinting();
}
