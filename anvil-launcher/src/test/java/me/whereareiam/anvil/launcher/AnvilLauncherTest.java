package me.whereareiam.anvil.launcher;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnvilLauncherTest {
	@Test
	void createsAnApiEngineWithoutStartingAScenarioOrMutatingOptions() {
		EngineOptions options = EngineOptions.builder().protocolId("mcprotocol").build();
		AnvilScenario invalid = AnvilScenario.builder().name("empty").entrypoint("missing").build();
		ScenarioEngine engine = AnvilLauncher.create(options);
		try (engine) {
			assertNull(options.getCacheDirectory());
			assertNull(options.getJavaSelection().getRequirement().getFeatureVersion());
			assertThrows(ScenarioValidationException.class, () -> engine.start(invalid));
		}

		engine.close();
		assertThrows(IllegalStateException.class, () -> engine.start(invalid));
	}

	@Test
	void assemblesServicesBeforeInstallingCallerExtensions() {
		List<String> installed = new ArrayList<>();
		var builder = AnvilLauncher.builder()
				.options(EngineOptions.builder().protocolId("missing-test-provider").build())
				.extension(registration -> installed.add("installed"));

		assertThrows(IllegalArgumentException.class, builder::build);
		assertTrue(installed.isEmpty());
		assertThrows(IllegalStateException.class, builder::build);
	}

	@Test
	void rollsBackCallerResourcesOnceAndPreservesTheInstallationFailure() {
		List<String> closed = new ArrayList<>();
		var installation = new IllegalStateException("Caller extension failed");
		var cleanup = new IllegalArgumentException("Caller resource cleanup failed");
		var builder = AnvilLauncher.builder()
				.options(EngineOptions.builder().protocolId("mcprotocol").build())
				.extension(registration -> registration.own(() -> closed.add("first")))
				.extension(registration -> {
					registration.own(() -> { closed.add("second"); throw cleanup; });
					throw installation;
				});

		assertSame(installation, assertThrows(IllegalStateException.class, builder::build));
		assertArrayEquals(new Throwable[]{cleanup}, installation.getSuppressed());
		assertEquals(List.of("second", "first"), closed);
		assertThrows(IllegalStateException.class, builder::build);
		assertEquals(List.of("second", "first"), closed);
	}
}
