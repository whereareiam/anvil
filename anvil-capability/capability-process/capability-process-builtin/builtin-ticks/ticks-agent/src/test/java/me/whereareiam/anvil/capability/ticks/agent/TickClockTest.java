package me.whereareiam.anvil.capability.ticks.agent;

import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickCount;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickWait;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickClockTest {
	private final TickClock clock = new TickClock();

	@Test
	void countsEveryTickTheServerCompletes() {
		clock.ticked();
		clock.ticked();

		assertEquals(2, clock.count().tick());
		assertEquals(clock.count().run(), clock.count().run());
		assertNotEquals(clock.count().run(), new TickClock().count().run());
	}

	@Test
	void answersAWaitOnceTheServerCompletesTheNextTick() throws Exception {
		clock.ticked();
		AgentTickCount seen = clock.count();
		CompletableFuture<AgentTickCount> next = CompletableFuture.supplyAsync(() -> next(seen, 10_000));

		assertThrows(TimeoutException.class, () -> next.get(100, TimeUnit.MILLISECONDS));
		clock.ticked();

		assertEquals(2, next.get(5, TimeUnit.SECONDS).tick());
	}

	@Test
	void answersAtOnceWhenATickAlreadyFollowedTheOneSeen() {
		AgentTickCount seen = clock.count();
		clock.ticked();

		assertEquals(1, next(seen, 10_000).tick());
	}

	@Test
	void answersTheSameCountOnceTheWaitPassesWithoutATick() {
		clock.ticked();
		long started = System.nanoTime();

		assertEquals(1, next(clock.count(), 50).tick());
		assertTrue(System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(50));
	}

	@Test
	void answersAtOnceAHostThatSawAnotherProcess() {
		clock.ticked();
		AgentTickCount earlier = AgentTickCount.builder().run("earlier process").tick(500).build();

		assertEquals(clock.count(), next(earlier, 10_000));
	}

	private AgentTickCount next(AgentTickCount seen, long waitMillis) {
		try {
			return clock.next(AgentTickWait.builder().run(seen.run()).after(seen.tick()).waitMillis(waitMillis).build());
		} catch (InterruptedException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
