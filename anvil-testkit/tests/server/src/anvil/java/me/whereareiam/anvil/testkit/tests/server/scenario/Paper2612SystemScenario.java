package me.whereareiam.anvil.testkit.tests.server.scenario;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Direct Paper 26.1.2 release of the live matrix, which behaviour tests and the IDE start on its own.
 */
public final class Paper2612SystemScenario implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		return CompatibilityScenarioFactory.releaseScenario(MinecraftVersion.parse("26.1.2"));
	}
}
