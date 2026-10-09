package me.whereareiam.anvil.api.model.process.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ProcessTimeoutsTest {
	@Test
	void scenarioStartupOverrideKeepsEngineShutdownDefault() {
		var defaults = ProcessTimeouts.builder().startup(Duration.ofMinutes(2)).shutdown(Duration.ofSeconds(15)).build();
		var scenario = ProcessTimeouts.builder().startup(Duration.ofMinutes(5)).build();

		var resolved = scenario.withDefaults(defaults);

		assertEquals(Duration.ofMinutes(5), resolved.getStartup());
		assertEquals(Duration.ofSeconds(15), resolved.getShutdown());
		assertNull(scenario.getShutdown());
		assertEquals(Duration.ofMinutes(2), defaults.getStartup());
	}

	@Test
	void scenarioShutdownOverrideKeepsEngineStartupDefault() {
		var defaults = ProcessTimeouts.builder().startup(Duration.ofMinutes(3)).shutdown(Duration.ofSeconds(15)).build();
		var scenario = ProcessTimeouts.builder().shutdown(Duration.ofSeconds(30)).build();

		var resolved = scenario.withDefaults(defaults);

		assertEquals(Duration.ofMinutes(3), resolved.getStartup());
		assertEquals(Duration.ofSeconds(30), resolved.getShutdown());
		assertEquals(defaults, ProcessTimeouts.builder().build().withDefaults(defaults));
	}
}
