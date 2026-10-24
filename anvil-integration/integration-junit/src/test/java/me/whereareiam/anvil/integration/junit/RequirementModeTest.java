package me.whereareiam.anvil.integration.junit;

import me.whereareiam.anvil.api.exception.scenario.ScenarioRequirementsException;
import me.whereareiam.anvil.engine.config.EngineProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.opentest4j.TestAbortedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequirementModeTest {
	private final ScenarioRequirementsException refused =
			new ScenarioRequirementsException("premium", "local", List.of("its processes may not reach api.mojang.com"));

	@AfterEach
	void clearProperty() {
		System.clearProperty(EngineProperties.REQUIREMENTS_PROPERTY);
	}

	@Test
	void skipsUnlessTheRunAsksForStrictMode() {
		assertEquals(RequirementMode.SKIP, RequirementMode.fromSystemProperties());

		System.setProperty(EngineProperties.REQUIREMENTS_PROPERTY, "Strict");
		assertEquals(RequirementMode.STRICT, RequirementMode.fromSystemProperties());
	}

	@Test
	void rejectsAnUnknownMode() {
		System.setProperty(EngineProperties.REQUIREMENTS_PROPERTY, "ignore");

		assertThrows(ExtensionConfigurationException.class, RequirementMode::fromSystemProperties);
	}

	@Test
	void skipsWithTheRefusalAsCauseOrFailsWithTheRefusalItself() {
		RuntimeException skipped = RequirementMode.SKIP.refuse(refused);
		assertInstanceOf(TestAbortedException.class, skipped);
		assertSame(refused, skipped.getCause());
		assertEquals(refused.getMessage(), skipped.getMessage());

		assertSame(refused, RequirementMode.STRICT.refuse(refused));
	}
}
