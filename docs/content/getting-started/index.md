---
title: Getting started
description: Add Anvil to a Gradle project, define a scenario, and run your first live test.
---

# Getting started

Anvil runs a real Minecraft server or proxy around a JUnit journey. You describe the processes and
artifacts in a scenario, create native-protocol players when the journey needs them, and let the
test assert what the server, proxy, and player observe.

This section takes you from an empty Gradle project to a passing live test:

- [Install Anvil](./installation/index.md) and choose the Gradle plugins, protocol provider, and
  platform units your project needs.
- [Run your first scenario](./first-scenario/index.md) against a pinned Paper distribution and a
  packaged plugin artifact.
- [Learn the core concepts](./concepts/index.md) before building scenarios with multiple processes,
  players, or capabilities.

## Before you begin

You need:

- Java 21 or newer for the Gradle build. Anvil can select another Java runtime for a managed process
  when that process requires it.
- A Java project using Gradle and JUnit Jupiter.
- A server or proxy distribution that the selected platform provider supports.
- Explicit acceptance of the [Minecraft EULA](https://www.minecraft.net/eula) for automated server
  runs.

Anvil is a framework and library for developers. It does not install itself into a running server,
replace your plugin's platform dependency, or provide a full game client. Your test project builds
the plugin, Anvil starts the declared environment, and a lightweight client speaks the native
Minecraft protocol.

## Where the files go

The Gradle plugin creates an `anvil` source set alongside the normal Java source sets:

```text
src/main/       plugin or application code
src/test/       ordinary unit tests
src/anvil/      scenario definitions and live journeys
```

Run live journeys with `./gradlew anvilTest`. The normal `test` task remains focused on unit tests;
you can opt into live journeys with `-Panvil.testMode=full`.

Once the first test works, continue with [Writing tests](../writing-tests/index.md) for scenario
catalogs, workspaces, players, and capabilities, or [Running environments](../running-environments/index.md)
for platform, Java, proxy, and process configuration.

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
