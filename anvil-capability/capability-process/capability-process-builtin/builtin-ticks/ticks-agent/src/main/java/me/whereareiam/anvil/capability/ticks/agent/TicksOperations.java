package me.whereareiam.anvil.capability.ticks.agent;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickCount;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickWait;
import org.jetbrains.annotations.NotNull;

/**
 * The agent channel of the ticks capability: the operations its host provider requests and every platform side
 * answers, in the namespace the platform sides own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TicksOperations {
	/**
	 * The namespace the operations belong to, which the platform sides own.
	 */
	public static final @NotNull String NAMESPACE = "me.whereareiam.anvil.ticks";
	/**
	 * Tells how many ticks the server has run, without waiting.
	 */
	public static final @NotNull AgentOperation<Void, AgentTickCount> COUNT = AgentOperation.<Void, AgentTickCount>builder()
			.name(NAMESPACE + ".count").requestType(Void.class).responseType(AgentTickCount.class).build();
	/**
	 * Answers once the server has run a tick after the one the host saw last, or once the wait has passed without one.
	 */
	public static final @NotNull AgentOperation<AgentTickWait, AgentTickCount> NEXT =
			AgentOperation.<AgentTickWait, AgentTickCount>builder()
					.name(NAMESPACE + ".next").requestType(AgentTickWait.class).responseType(AgentTickCount.class).build();
	/**
	 * Has the server run a number of ticks as fast as it can, replacing a sprint already running, and answers the
	 * count the sprint starts from.
	 */
	public static final @NotNull AgentOperation<Integer, AgentTickCount> SPRINT = AgentOperation.<Integer, AgentTickCount>builder()
			.name(NAMESPACE + ".sprint").requestType(Integer.class).responseType(AgentTickCount.class).build();
	/**
	 * Stops a sprint, answering whether one was running.
	 */
	public static final @NotNull AgentOperation<Void, Boolean> STOP = AgentOperation.<Void, Boolean>builder()
			.name(NAMESPACE + ".stop").requestType(Void.class).responseType(Boolean.class).build();
}
