package me.whereareiam.anvil.testing.server.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Direct Paper 1.20.6 fixture for live Anvil system tests.
 */
public final class Paper1206SystemScenario implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		return CompatibilityScenarioCatalog.paperScenario("1.20.6", "151");
	}
}
