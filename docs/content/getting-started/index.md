---
title: Getting started
description: Configure the Anvil Gradle plugin and run a pinned Paper scenario from JUnit.
---

# Getting started

Use Java 21 or newer for the build. The snippets below use the release coordinate,
`0.0.1`; keep the Anvil plugin and platform-unit versions aligned.

## Configure repositories

Add these repositories to `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://registry.whereareiam.me/maven/packages")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }
}
```

For artifacts you publish locally while developing Anvil itself, add `mavenLocal()` before the
remote Anvil repository. See [building and publication](../contributing/publishing/index.md).

## Apply the plugin where the scenarios live

For a single-project plugin, use this `build.gradle.kts` configuration:

```kotlin
plugins {
    java
    id("me.whereareiam.anvil") version "0.0.1"
    id("me.whereareiam.anvil.platform.paper") version "0.0.1"
}

dependencies {
    add("anvilProtocols", anvil.protocols.mcprotocol)
}

anvil {
    acceptEula()
    protocol("mcprotocol")
    artifact("plugin-under-test", tasks.named("jar"))
}
```

Calling `acceptEula()` records your explicit acceptance of the
[Minecraft EULA](https://www.minecraft.net/eula). In a multi-project build, register the plugin
artifact with `artifact("plugin-under-test", project(":plugin"))` instead.

The umbrella plugin installs both workflows and the built-in capabilities. Platform units remain
explicit. Smaller installations can select the `me.whereareiam.anvil.scenarios` or
`me.whereareiam.anvil.junit` plugin and individual [capability units](../writing-tests/capabilities/index.md).

## Define the environment

Create `src/anvil/java/com/example/test/PaperScenario.java`:

```java
package com.example.test;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class PaperScenario implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.workspace(WorkspacePlan.builder()
						.asset(WorkspaceAsset.builder()
								.group("plugin-under-test")
								.source(AssetSource.artifact("plugin-under-test"))
								.target(Path.of("plugins", "plugin-under-test.jar"))
								.build())
						.build())
				.build();
		return AnvilScenario.builder().name("paper-plugin-test")
				.entrypoint("server").server(server).build();
	}
}
```

The registered JAR must be a valid plugin for the chosen server. Anvil copies the built artifact;
it does not supply your plugin's descriptor or platform implementation.

## Write the journey

Create `src/anvil/java/com/example/test/PlayerJoinTest.java`:

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.junit.AnvilTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerJoinTest {
	@Test
	@AnvilTest(PaperScenario.class)
	void observesAliceOnTheServer(ScenarioContext anvil) {
		var alice = anvil.players().create("Alice");
		var session = alice.capability(Session.class);
		session.connect();
		session.connected();
		assertEquals("Alice", alice.capability(Server.class)
				.joined("server").getObservedUsername());
	}
}
```

This verifies native login and agent-observed presence. Add assertions for your plugin's own
messages, commands, routing, or state after the player joins.

## Run it

```shell
./gradlew anvilTest
```

`src/main` holds your plugin, `src/test` holds ordinary tests, and `src/anvil` holds live scenarios
and journeys. `anvilTest` explicitly starts Minecraft. To include it when running the normal test
task, use `./gradlew test -Panvil.testMode=full`.

For a complete consumer-shaped example, browse
[proof-of-patience](https://github.com/whereareiam/anvil/tree/dev/examples/proof-of-patience).
