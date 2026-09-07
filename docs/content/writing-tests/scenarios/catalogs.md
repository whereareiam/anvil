---
title: Scenario catalogs and groups
description: Register multiple scenarios and deterministic groups for discovery and manual runs.
---

# Scenario catalogs and groups

Implement `AnvilScenarioProvider` when a build exposes a catalog of scenarios. The provider receives
a `ScenarioRegistry`, which keeps registration order and rejects duplicate scenario or group names.

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.scenario.AnvilScenarioProvider;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;

public final class CompatibilityScenarios implements AnvilScenarioProvider {
	@Override
	public void register(@NotNull ScenarioRegistry registry) {
		AnvilScenario paper = server("paper-1.21.11", Platforms.PAPER, Distribution.remote("1.21.11", "132"), "1.21.11");
		AnvilScenario spigot = server("spigot-1.21.11", Platforms.SPIGOT,
				Distribution.pinned("1.21.11", "6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd"), "1.21.11");

		registry.scenario(paper)
				.scenario(spigot)
				.group(ScenarioGroup.builder()
						.name("release")
						.scenario(paper.getName())
						.scenario(spigot.getName())
						.build());
	}

	private static AnvilScenario server(String name, String platform, Distribution distribution, String minecraftVersion) {
		return AnvilScenario.builder()
				.name(name)
				.entrypoint("server")
				.server(MinecraftServer.builder()
						.name("server")
						.platform(platform)
						.distribution(distribution)
						.minecraftVersion(minecraftVersion)
						.build())
				.build();
	}
}
```

Register the provider class with the Gradle Anvil extension. The catalog is used by scenario
listing, grouped manual runs, and matrix selection; a JUnit test that needs one static environment
can still select an `AnvilScenarioDefinition` directly.

```kotlin
anvil {
	// The class is in src/anvil and is available to Anvil's scenario classpath.
	scenarioProviders.add("example.scenario.CompatibilityScenarios")
}
```

Use exact `Distribution.remote` build identifiers or checksum-pinned selections in automated
groups. `latest` is reserved for manual scenarios and depends on provider support. A group stores
scenario names, so keep names stable when they are used by CI or a local command.

The foreground runner and its commands are documented in [Manual environments](../../running-environments/manual/index.md).
