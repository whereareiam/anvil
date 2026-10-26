package me.whereareiam.anvil.testkit.tests.server.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.integration.junit.AnvilScenarioFactory;
import me.whereareiam.anvil.testkit.tests.server.scenario.PhysicsScenarioFactory;
import org.jetbrains.annotations.NotNull;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Builds the scenario of a judge of the release under test, skipping the test where the pinned judge does not support
 * the release.
 */
public final class PhysicsScenarios implements AnvilScenarioFactory<PhysicsEnvironment> {
	@Override
	public @NotNull AnvilScenario create(@NotNull PhysicsEnvironment declaration) {
		MinecraftVersion version = PhysicsRelease.version();
		return switch (declaration.value()) {
			case VANILLA -> PhysicsScenarioFactory.vanillaScenario(version);
			case GRIM -> {
				assumeTrue(PhysicsScenarioFactory.supportsGrim(version), "The pinned Grim build does not support " + version);
				yield PhysicsScenarioFactory.grimScenario(version);
			}
			case SONAR -> {
				assumeTrue(PhysicsScenarioFactory.supportsSonar(version), "The pinned Sonar build does not support " + version);
				yield PhysicsScenarioFactory.sonarScenario(version);
			}
		};
	}
}
