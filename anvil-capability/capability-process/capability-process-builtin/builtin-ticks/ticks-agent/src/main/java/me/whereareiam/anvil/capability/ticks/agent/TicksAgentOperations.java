package me.whereareiam.anvil.capability.ticks.agent;

import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Answers the ticks capability's operations on one platform: each platform side extends it with the
 * {@link ServerTicks} of its platform, which it opens once the agent starts, reporting every tick its server completes
 * to the {@link TickClock} it is given. Counting and waiting for ticks run on the request's thread; starting and
 * stopping a sprint run on the platform's own thread, so a sprint starts from the count the server had between two
 * ticks.
 */
public abstract class TicksAgentOperations implements AgentOperationProvider {
	@Override
	public final @NotNull String id() {
		return TicksOperations.NAMESPACE;
	}

	@Override
	public final void install(@NotNull PlatformAgent platform, @NotNull AgentOperationRegistry registry) {
		TickClock clock = new TickClock();
		ServerTicks ticks = open(platform, clock);

		registry.register(TicksOperations.COUNT, (agent, ignored) -> clock.count());
		registry.register(TicksOperations.NEXT, (agent, wait) -> clock.next(wait));
		registry.register(TicksOperations.SPRINT, (agent, count) -> agent.call(() -> {
			ticks.sprint(count);
			return clock.count();
		}));
		registry.register(TicksOperations.STOP, (agent, ignored) -> agent.call(ticks::stopSprinting));
	}

	/**
	 * Opens the platform's tick rate control and starts reporting each tick its server completes.
	 *
	 * @param platform the platform agent, whose services reach the platform's API
	 * @param clock the clock to report each completed tick to, from the server's thread
	 * @return the platform's tick rate control
	 */
	protected abstract @NotNull ServerTicks open(@NotNull PlatformAgent platform, @NotNull TickClock clock);
}
