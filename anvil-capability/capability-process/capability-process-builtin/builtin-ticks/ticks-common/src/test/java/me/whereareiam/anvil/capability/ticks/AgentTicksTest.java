package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.api.model.Condition;
import me.whereareiam.anvil.capability.ticks.type.TickPace;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTicksTest {
	private static final Duration LONG = Duration.ofSeconds(10);
	private static final Duration SHORT = Duration.ofMillis(200);

	private final SimulatedServer server = new SimulatedServer();
	private final AgentTicks ticks = new AgentTicks("server", server, new OpenPaces("server", server));

	@Test
	void readsHowManyTicksTheServerHasRun() {
		server.run(42);

		assertEquals(42, ticks.tick());
	}

	@Test
	void waitsForTheServerToRunTicks() {
		server.run(10);

		ticks.await(5, LONG);

		assertEquals(15, ticks.tick());
		assertEquals(5, server.waits);
	}

	@Test
	void countsEveryTickTheServerRanAtTheFastestPace() {
		try (Pacing fast = ticks.pace(TickPace.fastest())) {
			ticks.await(100, LONG);
		}

		assertEquals(100, ticks.tick());
		assertEquals(10, server.waits);
	}

	@Test
	void namesHowManyTicksAFrozenServerRanWhenItGivesUp() {
		server.frozen = true;

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ticks.await(5, SHORT));

		assertEquals("'server' ran 0 of 5 ticks within PT0.2S", failure.getMessage());
	}

	@Test
	void checksAConditionAfterEveryTickAndReturnsTheStateItHeldFor() {
		List<Long> seen = new ArrayList<>();

		long held = ticks.until(Condition.of("the server has run three ticks", () -> {
			seen.add(server.clock.count().tick());
			return server.clock.count().tick();
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
	void letsTheConditionItselfFailTheWait() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ticks.until(
				Condition.of("the answer arrives", () -> {
					throw new IllegalStateException("no answer");
				}), LONG));

		assertEquals("no answer", failure.getMessage());
	}

	@Test
	void countsOnFromTheFirstTickOfARestartedServer() {
		server.run(500);
		server.restartAtNextWait = true;

		ticks.await(3, LONG);

		assertEquals(3, ticks.tick());
	}

	@Test
	void refusesTicksAndTimeoutsThatAreNotPositive() {
		assertThrows(IllegalArgumentException.class, () -> ticks.await(0, LONG));
		assertThrows(IllegalArgumentException.class, () -> ticks.await(5, Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> ticks.until(Condition.of("x", () -> false), Duration.ofSeconds(-1)));
	}
}
