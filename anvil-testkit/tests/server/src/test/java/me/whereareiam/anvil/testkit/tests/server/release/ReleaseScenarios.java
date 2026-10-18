package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.ProcessLifetime;
import me.whereareiam.anvil.integration.junit.AnvilScenarioFactory;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the scenario of the release under test, whose server outlives each test so the release suite starts it
 * once.
 */
public final class ReleaseScenarios implements AnvilScenarioFactory<Release> {
	@Override
	public @NotNull AnvilScenario create(@NotNull Release declaration) {
		AnvilScenario release = CompatibilityScenarioFactory.releaseScenario(TestedRelease.version());

		return release.toBuilder()
				.clearServers()
				.servers(release.getServers().stream()
						.map(server -> server.toBuilder().lifetime(ProcessLifetime.ENGINE).build())
						.toList())
				.build();
	}
}
