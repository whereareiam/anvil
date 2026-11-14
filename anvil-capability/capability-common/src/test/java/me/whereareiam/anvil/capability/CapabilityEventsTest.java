package me.whereareiam.anvil.capability;

import me.whereareiam.anvil.capability.api.event.lifecycle.Closed;
import me.whereareiam.anvil.capability.api.event.scenario.ScenarioFinished;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityEventsTest {
	@Test
	void runsOnlyTheEventsActionsInReverseRegistrationOrderEachTimeItIsReported() {
		List<String> runs = new ArrayList<>();
		CapabilityEvents events = new CapabilityEvents("Process 'lobby'", Set.of(ScenarioFinished.class));
		events.on(ScenarioFinished.class, finished -> runs.add("first:" + finished.isPassed()));
		events.on(Closed.class, closed -> runs.add("close"));
		events.on(ScenarioFinished.class, finished -> runs.add("second:" + finished.isPassed()));

		assertNull(events.fire(new ScenarioFinished(true), null));
		assertNull(events.fire(new ScenarioFinished(false), null));

		assertEquals(List.of("second:true", "first:true", "second:false", "first:false"), runs);
	}

	@Test
	void runsEveryActionAndReportsTheFirstFailureWithTheLaterOnesSuppressed() {
		List<String> runs = new ArrayList<>();
		IllegalStateException earlier = new IllegalStateException("earlier");
		IllegalStateException first = new IllegalStateException("first");
		IllegalStateException second = new IllegalStateException("second");
		CapabilityEvents events = new CapabilityEvents("Process 'lobby'", Set.of(ScenarioFinished.class));
		events.on(ScenarioFinished.class, finished -> {
			runs.add("oldest");
			throw second;
		});
		events.on(ScenarioFinished.class, finished -> runs.add("middle"));
		events.on(ScenarioFinished.class, finished -> {
			runs.add("newest");
			throw first;
		});

		assertSame(first, events.fire(new ScenarioFinished(true), null));
		assertEquals(List.of("newest", "middle", "oldest"), runs);
		assertEquals(List.of(second), List.of(first.getSuppressed()));

		assertSame(earlier, events.fire(new ScenarioFinished(true), earlier));
		assertEquals(List.of(first, second), List.of(earlier.getSuppressed()));
	}

	@Test
	void refusesAnEventItsOwnerNeverReportsInsteadOfNeverCallingTheAction() {
		CapabilityEvents events = new CapabilityEvents("Player 'Alice'", Set.of());

		CapabilityException refused = assertThrows(CapabilityException.class,
				() -> events.on(ScenarioFinished.class, finished -> { }));

		assertEquals("Player 'Alice' never reports ScenarioFinished; it reports [Closed]", refused.getMessage());
		assertThrows(IllegalArgumentException.class, () -> events.fire(new ScenarioFinished(true), null));
	}

	@Test
	void reportsClosedOnceAndNothingAfterwardsAndRunsALateCloseActionAtOnce() {
		List<String> runs = new ArrayList<>();
		CapabilityEvents events = new CapabilityEvents("Process 'lobby'", Set.of(ScenarioFinished.class));
		events.on(ScenarioFinished.class, finished -> runs.add("scenario"));
		events.on(Closed.class, closed -> runs.add("close"));

		assertNull(events.fire(Closed.INSTANCE, null));
		assertNull(events.fire(Closed.INSTANCE, null));
		assertNull(events.fire(new ScenarioFinished(true), null));
		events.on(Closed.class, closed -> runs.add("late"));

		assertEquals(List.of("close", "late"), runs);
		assertTrue(assertThrows(CapabilityException.class, () -> events.on(ScenarioFinished.class, finished -> { }))
				.getMessage().contains("is closed"));
	}

	@Test
	void aCapabilitySetReportsClosedOnlyWhenItCloses() {
		List<String> runs = new ArrayList<>();
		CapabilitySet<?> set = new CapabilitySet<>("Player 'Alice'", Set.of());
		var context = set.context("session", Set.of());
		context.on(Closed.class, closed -> runs.add("first"));
		context.on(Closed.class, closed -> runs.add("second"));

		assertThrows(IllegalArgumentException.class, () -> set.report(Closed.INSTANCE, null));
		set.close();

		assertEquals(List.of("second", "first"), runs);
	}
}
