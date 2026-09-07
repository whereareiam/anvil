package me.whereareiam.anvil.launcher;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnvilLauncherTest {
	@Test
	void createsAnApiEngineWithoutStartingAScenarioOrMutatingOptions() {
		EngineOptions options = EngineOptions.builder().protocolId("mcprotocol").build();
		AnvilScenario invalid = AnvilScenario.builder().name("empty").entrypoint("missing").build();
		ScenarioEngine engine = AnvilLauncher.create(options);
		try (engine) {
			assertNull(options.getCacheDirectory());
			assertNull(options.getJavaRequirement().getFeatureVersion());
			assertThrows(ScenarioValidationException.class, () -> engine.start(invalid));
		}

		engine.close();
		assertThrows(IllegalStateException.class, () -> engine.start(invalid));
	}
}
