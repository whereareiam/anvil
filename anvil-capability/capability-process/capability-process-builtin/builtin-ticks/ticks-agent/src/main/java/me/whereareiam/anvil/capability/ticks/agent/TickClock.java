package me.whereareiam.anvil.capability.ticks.agent;

import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickCount;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickWait;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Counts the ticks a server's process completes, which its platform side reports from the server's thread, and lets
 * the agent's request threads wait for the next one. Its run names the process, so a host that saw the count of an
 * earlier process learns that the server restarted.
 */
public final class TickClock {
	/**
	 * The longest a request waits for a tick, well within the read timeout of the agent's host client, which waits
	 * five seconds for every answer.
	 */
	static final long MAXIMUM_WAIT_MILLIS = 2_000;

	private final String run = UUID.randomUUID().toString();
	private long tick;

	/**
	 * Records that the server completed a tick and wakes the requests waiting for one.
	 */
	public synchronized void ticked() {
		tick++;
		notifyAll();
	}

	/**
	 * Tells how many ticks the server has completed, without waiting.
	 *
	 * @return the count
	 */
	public synchronized @NotNull AgentTickCount count() {
		return AgentTickCount.builder().run(run).tick(tick).build();
	}

	/**
	 * Waits until the server has completed a tick after the one a host saw last, or until the wait has passed without
	 * one, and answers the count then. A host that saw another process's count is answered at once.
	 *
	 * @param wait the count the host saw last and how long to wait at most, which is capped at two seconds
	 * @return the count
	 * @throws InterruptedException when the request thread is interrupted, as when the agent stops
	 */
	public synchronized @NotNull AgentTickCount next(@NotNull AgentTickWait wait) throws InterruptedException {
		if (!run.equals(wait.run())) return count();

		long limit = Math.max(0, Math.min(wait.waitMillis(), MAXIMUM_WAIT_MILLIS));
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limit);
		while (tick <= wait.after()) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) break;

			TimeUnit.NANOSECONDS.timedWait(this, remaining);
		}

		return count();
	}
}
