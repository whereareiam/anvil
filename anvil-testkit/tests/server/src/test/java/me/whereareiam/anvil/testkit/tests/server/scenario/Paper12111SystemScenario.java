package me.whereareiam.anvil.testkit.tests.server.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Direct Paper 1.21.11 fixture for live Anvil system tests.
 */
public final class Paper12111SystemScenario implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		return CompatibilityScenarioCatalog.paperScenario("1.21.11", "132");
	}
}
