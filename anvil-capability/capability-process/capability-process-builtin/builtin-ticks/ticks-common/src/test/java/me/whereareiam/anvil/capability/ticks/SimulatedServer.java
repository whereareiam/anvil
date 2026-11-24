package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.capability.api.exception.ChannelUnavailableException;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.ticks.agent.TickClock;
import me.whereareiam.anvil.capability.ticks.agent.TicksOperations;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickPace;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickWait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A server whose agent answers the ticks channel over a real {@link TickClock}: it runs one tick for each wait, ten
 * while it sprints and none while it is frozen, keeps the pace it was given, and can restart its process.
 */
final class SimulatedServer implements RequestChannel {
	static final AgentTickPace NORMAL = AgentTickPace.builder().rate(20).sprinting(false).build();

	final List<AgentTickPace> paces = new ArrayList<>();
	TickClock clock = new TickClock();
	AgentTickPace pace = NORMAL;
	int waits;
	boolean frozen;
	boolean stopped;
	boolean restartAtNextWait;

	void run(int ticks) {
		for (int tick = 0; tick < ticks; tick++)
			clock.ticked();
	}

	void restart() {
		clock = new TickClock();
		pace = NORMAL;
	}

	@Override
	@SuppressWarnings("unchecked")
	public synchronized <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> operation, @Nullable Q request) {
		if (stopped) throw new ChannelUnavailableException("The server stopped", new IOException("Connection refused"));

		String name = operation.id();
		if (name.equals(TicksOperations.COUNT.name())) return (R) clock.count();
		if (name.equals(TicksOperations.NEXT.name())) return (R) next((AgentTickWait) request);
		if (!name.equals(TicksOperations.PACE.name())) throw new AssertionError("Unexpected operation " + name);

		AgentTickPace previous = pace;
		pace = (AgentTickPace) request;
		paces.add(pace);

		return (R) previous;
	}

	private @NotNull Object next(@NotNull AgentTickWait wait) {
		waits++;
		if (restartAtNextWait) {
			restartAtNextWait = false;
			restart();
		}
		if (frozen) return frozen(wait);

		run(pace.sprinting() ? 10 : 1);
		try {
			return clock.next(wait);
		} catch (InterruptedException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private @NotNull Object frozen(@NotNull AgentTickWait wait) {
		try {
			wait(Math.max(1, wait.waitMillis()));
		} catch (InterruptedException exception) {
			throw new IllegalStateException(exception);
		}

		return clock.count();
	}
}
