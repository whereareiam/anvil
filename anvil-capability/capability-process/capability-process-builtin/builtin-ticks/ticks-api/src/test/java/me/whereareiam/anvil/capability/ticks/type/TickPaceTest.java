package me.whereareiam.anvil.capability.ticks.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TickPaceTest {
	@Test
	void namesTheNormalAndFastestPacesOnce() {
		assertSame(TickPace.normal(), TickPace.normal());
		assertSame(TickPace.fastest(), TickPace.fastest());
		assertInstanceOf(TickPace.Normal.class, TickPace.normal());
		assertInstanceOf(TickPace.Fastest.class, TickPace.fastest());
	}

	@Test
	void keepsTheRateOfARatePace() {
		assertEquals(TickPace.rate(5), TickPace.rate(5));
		assertEquals(5, ((TickPace.Rate) TickPace.rate(5)).getTicksPerSecond());
		assertEquals(10_000, ((TickPace.Rate) TickPace.rate(TickPace.MAXIMUM_RATE)).getTicksPerSecond());
	}

	@Test
	void refusesARateNoServerRuns() {
		assertThrows(IllegalArgumentException.class, () -> TickPace.rate(0.5f));
		assertThrows(IllegalArgumentException.class, () -> TickPace.rate(10_001));
		assertThrows(IllegalArgumentException.class, () -> TickPace.rate(Float.NaN));
	}
}
