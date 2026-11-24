package me.whereareiam.anvil.capability.ticks.agent;

import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickPace;
import org.jetbrains.annotations.NotNull;

/**
 * Answers the ticks capability's operations on one platform: each platform side extends it with the
 * {@link ServerTicks} of its platform, which it opens once the agent starts, reporting every tick its server completes
 * to the {@link TickClock} it is given. Counting and waiting for ticks run on the request's thread; setting the pace
 * runs on the platform's own thread, between two ticks.
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
		registry.register(TicksOperations.PACE, (agent, pace) -> agent.call(() -> {
			AgentTickPace previous = ticks.pace();
			ticks.pace(pace);
			return previous;
		}));
	}

	/**
	 * Opens the platform's control of its tick pace and starts reporting each tick its server completes.
	 *
	 * @param platform the platform agent, whose services reach the platform's API
	 * @param clock the clock to report each completed tick to, from the server's thread
	 * @return the platform's control of its tick pace
	 */
	protected abstract @NotNull ServerTicks open(@NotNull PlatformAgent platform, @NotNull TickClock clock);
}
