package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.api.model.Condition;
import me.whereareiam.anvil.capability.api.exception.ChannelUnavailableException;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.ticks.agent.TickClock;
import me.whereareiam.anvil.capability.ticks.agent.TicksOperations;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickWait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTicksTest {
	private static final Duration LONG = Duration.ofSeconds(10);
	private static final Duration SHORT = Duration.ofMillis(200);

	private final SimulatedServer server = new SimulatedServer();
	private final AgentTicks ticks = new AgentTicks("server", server);

	@Test
	void readsHowManyTicksTheServerHasRun() {
		server.run(42);

		assertEquals(42, ticks.tick());
	}

	@Test
	void waitsForTheServerToRunTicksAtItsOwnPace() {
		server.run(10);

		ticks.await(5, LONG);

		assertEquals(15, ticks.tick());
		assertEquals(List.of(), server.sprints);
	}

	@Test
	void namesHowManyTicksAFrozenServerRanWhenItGivesUp() {
		server.frozen = true;

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ticks.await(5, SHORT));

		assertEquals("'server' ran 0 of 5 ticks within PT0.2S", failure.getMessage());
	}

	@Test
	void sprintsTheTicksAndLetsTheSprintEndOnItsOwn() {
		ticks.sprint(100, LONG);

		assertTrue(ticks.tick() >= 100);
		assertEquals(List.of(100), server.sprints);
		assertEquals(0, server.stops);
		ticks.finishScenario();
		assertEquals(0, server.stops);
	}

	@Test
	void stopsASprintThatDidNotEndInTime() {
		server.frozen = true;

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ticks.sprint(100, SHORT));

		assertEquals("'server' sprinted 0 of 100 ticks within PT0.2S", failure.getMessage());
		assertEquals(1, server.stops);
	}

	@Test
	void checksAConditionAfterEveryTickAndReturnsTheStateItHeldFor() {
		List<Long> seen = new ArrayList<>();

		long held = ticks.until(Condition.of("the server has run three ticks", () -> {
			seen.add(server.clock.count().getTick());
			return server.clock.count().getTick();
		}, tick -> tick >= 3), LONG);

		assertEquals(3, held);
		assertEquals(List.of(0L, 1L, 2L, 3L), seen);
	}

	@Test
	void returnsAtOnceWhenTheConditionAlreadyHolds() {
		assertEquals(true, ticks.until(Condition.of("it holds", () -> true), LONG));
		assertEquals(0, server.waits);
	}

	@Test
	void namesWhatItAwaitedAndWhatItSawLastWhenItGivesUp() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ticks.until(
				Condition.of("the wheat is fully grown", () -> "age=3", block -> block.equals("age=7")), SHORT));

		assertTrue(failure.getMessage().startsWith("Gave up on 'server' after 0.2 s and "), failure.getMessage());
		assertTrue(failure.getMessage().endsWith(" ticks waiting until the wheat is fully grown; last seen: age=3"),
				failure.getMessage());
	}

	@Test
	void stopsTheSprintOnceTheConditionHolds() {
		long held = ticks.sprintUntil(Condition.of("the server has run 50 ticks", () -> server.clock.count().getTick(),
				tick -> tick >= 50), 10_000, LONG);

		assertTrue(held >= 50);
		assertEquals(List.of(10_000), server.sprints);
		assertEquals(1, server.stops);
	}

	@Test
	void givesUpOnceTheSprintRanItsTicksWithoutTheConditionHolding() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ticks.sprintUntil(
				Condition.of("the chunk is unloaded", () -> false), 30, LONG));

		assertTrue(failure.getMessage().contains(" 30 ticks waiting until the chunk is unloaded; last seen: false"),
				failure.getMessage());
		assertEquals(1, server.stops);
	}

	@Test
	void stopsTheSprintWhenTheConditionItselfFails() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ticks.sprintUntil(
				Condition.of("the answer arrives", () -> {
					throw new IllegalStateException("no answer");
				}), 30, LONG));

		assertEquals("no answer", failure.getMessage());
		assertEquals(1, server.stops);
	}

	@Test
	void countsOnFromTheFirstTickOfARestartedServer() {
		server.run(500);
		server.restartAtNextWait = true;

		ticks.await(3, LONG);

		assertEquals(3, ticks.tick());
	}

	@Test
	void stopsAnUnfinishedSprintWhenTheScenarioFinishes() {
		server.frozen = true;
		CompletableFuture<Void> sprinting = CompletableFuture.runAsync(() -> ticks.sprint(100, Duration.ofSeconds(1)));
		server.awaitSprint();

		ticks.finishScenario();

		assertEquals(1, server.stops);
		assertThrows(ExecutionException.class, () -> sprinting.get(5, TimeUnit.SECONDS));
	}

	@Test
	void forgivesAServerThatStoppedWithItsSprint() {
		server.frozen = true;
		CompletableFuture<Void> sprinting = CompletableFuture.runAsync(() -> ticks.sprint(100, LONG));
		server.awaitSprint();
		server.stopped = true;

		ticks.finishScenario();

		ExecutionException failure = assertThrows(ExecutionException.class, () -> sprinting.get(5, TimeUnit.SECONDS));
		assertInstanceOf(ChannelUnavailableException.class, failure.getCause());
	}

	@Test
	void refusesTicksAndTimeoutsThatAreNotPositive() {
		assertThrows(IllegalArgumentException.class, () -> ticks.await(0, LONG));
		assertThrows(IllegalArgumentException.class, () -> ticks.sprint(-1, LONG));
		assertThrows(IllegalArgumentException.class, () -> ticks.until(Condition.of("x", () -> false), Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> ticks.sprintUntil(Condition.of("x", () -> false), 0, LONG));
		assertThrows(IllegalArgumentException.class, () -> ticks.sprint(100, Duration.ZERO));
		assertThrows(IllegalArgumentException.class,
				() -> ticks.sprintUntil(Condition.of("x", () -> false), 100, Duration.ofSeconds(-1)));
		assertEquals(List.of(), server.sprints);
	}

	/**
	 * A server whose agent answers the ticks channel: it runs one tick for each wait, ten while it sprints, none while
	 * it is frozen, and can restart its process.
	 */
	private static final class SimulatedServer implements RequestChannel {
		private final List<Integer> sprints = new ArrayList<>();
		private TickClock clock = new TickClock();
		private int stops;
		private int waits;
		private int sprintRemaining;
		private boolean frozen;
		private volatile boolean stopped;
		private boolean restartAtNextWait;

		private void run(int ticks) {
			for (int tick = 0; tick < ticks; tick++)
				clock.ticked();
		}

		private synchronized void awaitSprint() {
			while (sprints.isEmpty()) {
				try {
					wait();
				} catch (InterruptedException exception) {
					throw new IllegalStateException(exception);
				}
			}
		}

		@Override
		@SuppressWarnings("unchecked")
		public synchronized <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> operation, @Nullable Q request) {
			if (stopped) throw new ChannelUnavailableException("The server stopped", new IOException("Connection refused"));

			String name = operation.getId();
			if (name.equals(TicksOperations.COUNT.getName())) return (R) clock.count();
			if (name.equals(TicksOperations.NEXT.getName())) return (R) next((AgentTickWait) request);
			if (name.equals(TicksOperations.SPRINT.getName())) return (R) sprint((Integer) request);
			if (!name.equals(TicksOperations.STOP.getName())) throw new AssertionError("Unexpected operation " + name);

			stops++;
			boolean sprinting = sprintRemaining > 0;
			sprintRemaining = 0;

			return (R) Boolean.valueOf(sprinting);
		}

		private @NotNull Object next(@NotNull AgentTickWait wait) {
			waits++;
			if (restartAtNextWait) {
				restartAtNextWait = false;
				clock = new TickClock();
			}
			if (frozen) return frozen(wait);

			int ticks = sprintRemaining > 0 ? Math.min(10, sprintRemaining) : 1;
			sprintRemaining -= Math.min(sprintRemaining, ticks);
			run(ticks);
			try {
				return clock.next(wait);
			} catch (InterruptedException exception) {
				throw new IllegalStateException(exception);
			}
		}

		private @NotNull Object frozen(@NotNull AgentTickWait wait) {
			try {
				wait(Math.max(1, wait.getWaitMillis()));
			} catch (InterruptedException exception) {
				throw new IllegalStateException(exception);
			}

			return clock.count();
		}

		private @NotNull Object sprint(int ticks) {
			sprints.add(ticks);
			sprintRemaining = ticks;
			notifyAll();

			return clock.count();
		}
	}
}
