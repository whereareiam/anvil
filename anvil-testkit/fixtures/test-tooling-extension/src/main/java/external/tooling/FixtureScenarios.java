package external.tooling;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;

/**
 * Uses a real Gradle-produced process artifact and creates a player with an external capability.
 */
public final class FixtureScenarios implements AnvilScenarioDefinition {
	@Override
	public AnvilScenario define() {
		return AnvilScenario.builder().name("external-tooling").entrypoint("server")
				.server(MinecraftServer.builder().name("server").platform("fixture-tooling")
						.minecraftVersion("1.21.11").memoryMegabytes(256).distribution(Distribution.artifact("fixture-process")).build())
				.setupHook(context -> context.players().create("External"))
				.build();
	}
}
