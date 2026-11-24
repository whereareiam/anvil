package me.whereareiam.anvil.api.model;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {
	@Test
	void looksAtTheStateOnEveryObservationAndTestsIt() {
		AtomicInteger age = new AtomicInteger(3);
		Condition<Integer> grown = Condition.of("the wheat is fully grown", age::get, value -> value == 7);

		assertEquals("the wheat is fully grown", grown.getDescription());
		Integer seen = grown.observe();
		assertEquals(3, seen);
		assertFalse(grown.holds(seen));
		age.set(7);
		assertTrue(grown.holds(grown.observe()));
	}

	@Test
	void reportsACheckWithoutAStateAsItsAnswer() {
		AtomicInteger checks = new AtomicInteger();
		Condition<Boolean> passed = Condition.of("the third check passes", () -> checks.incrementAndGet() >= 3);

		assertEquals(false, passed.observe());
		assertFalse(passed.holds(passed.observe()));
		Boolean third = passed.observe();
		assertEquals(true, third);
		assertTrue(passed.holds(third));
	}
}
