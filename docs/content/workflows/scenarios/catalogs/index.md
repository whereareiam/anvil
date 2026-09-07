---
title: Catalogs and groups
description: Register reusable named environments and organize them for interactive selection.
---

A catalog registers the environments you want to launch by name. A group selects an ordered subset
for one interactive session. Both use ordinary Java declarations, so keep your plugin artifacts,
configuration, and setup behavior with the environment rather than recreating them in terminal steps.

## Prepare a development catalog

Use the [installed Anvil build](../../../getting-started/installation/index.md), including its Paper
platform unit and MCProtocol dependency. For a smaller installation, apply
`me.whereareiam.anvil.scenarios` to obtain the foreground task.

Save this class as `src/anvil/java/com/example/test/DevelopmentScenarios.java`:

```java
package com.example.test;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.scenario.AnvilScenarioProvider;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;

public final class DevelopmentScenarios implements AnvilScenarioProvider {
	@Override
	public void register(@NotNull ScenarioRegistry registry) {
		var server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.build();
		registry.scenario(AnvilScenario.builder()
				.name("local-paper")
				.entrypoint(server.getName())
				.server(server)
				.manual(true)
				.build());
		registry.group(ScenarioGroup.builder()
				.name("development")
				.scenario("local-paper")
				.build());
	}
}
```

The provider class must have an accessible no-argument constructor; this public class gets one
without an explicit constructor. The example prepares a pinned Paper server with an offline game
listener. Add your [plugin artifact and configuration](../../../building-blocks/environments/workspaces/assets/index.md)
to its workspace to make it useful for your plugin.

The scenario's `.manual(true)` records its intended use. It does not accept the EULA, select an
online account, or enable LAN access by itself.

## Register and inspect the catalog

Add to the existing `anvil` block in `build.gradle.kts`:

```kotlin
anvil {
	acceptEula()
	scenarioProviders.add("com.example.test.DevelopmentScenarios")
}
```

Then run:

```shell
./gradlew anvilScenario --list --console=plain
```

The output should list `local-paper (manual)` and `development -> [local-paper]`. Listing evaluates
the catalog without starting its servers. Continue with [running scenarios](../running/index.md)
to launch that environment.

## Grow and reuse the catalog

Register more environments with `registry.scenario(...)` and add their names to a `ScenarioGroup`
using `.scenario(name)`. Scenario names must be unique, and group names must be unique, within their
respective registries. Registration order is preserved; starting a group launches its first member.
Every group member must name a registered scenario.

If you already have a reusable `PaperScenario` definition, evaluate `new PaperScenario().define()`
and derive a manual variant with `toBuilder().name("paper-manual").manual(true).build()`.
That reuses the same environment inputs that JUnit can select directly. Catalog groups control runner
selection; they do not automatically execute a test against every member.

Use [setup hooks](../../../building-blocks/environments/definitions/index.md#prepare-live-state) for repeatable live preparation,
including simulated players when needed. Choose a [persistent workspace](../../../building-blocks/environments/workspaces/persistence/index.md)
if files should survive separate interactive runs.
