---
title: Embedding Anvil
description: Start scenarios from a Java application with explicit provider dependencies and engine ownership.
---

Embed Anvil when your application owns scenario startup and cleanup rather than delegating them to
JUnit or Gradle's foreground runner. Use `AnvilLauncher` to create a `ScenarioEngine` and keep the
engine and its contexts in try-with-resources.

## Assemble the application classpath

Use aligned artifacts under `me.whereareiam.anvil`. For a Paper environment using the bundled
capabilities and MCProtocol, the dependency fragment in your application build is:

```kotlin
val anvilVersion = providers.gradleProperty("anvilVersion").get()

dependencies {
	implementation("me.whereareiam.anvil:default:$anvilVersion")
	implementation("me.whereareiam.anvil:launcher:$anvilVersion")

	runtimeOnly("me.whereareiam.anvil:platform-bukkit-agent:$anvilVersion")
	runtimeOnly("me.whereareiam.anvil:platform-paper-provider:$anvilVersion")
	runtimeOnly("me.whereareiam.anvil:protocol-mcprotocol:$anvilVersion")
}
```

Set `anvilVersion` to your chosen Anvil version in `gradle.properties` and configure the repositories
from [installation](../../getting-started/installation/index.md). Apply your normal Java/application
build plugins. Add the provider and matching agent artifacts for additional platforms.
Use the [Gradle plugin reference](../gradle/index.md) for the unit-to-artifact mapping.

The launcher supplies engine composition and execution implementations. It does not select a
platform, native protocol provider, or capability set for your application. Preserve service
descriptors if you assemble another shaded JAR around these dependencies.

## Start a supplied scenario

Put this helper at `src/main/java/EmbeddedScenario.java` in your application. Pass an `AnvilScenario` containing
an entrypoint and any desired processes. If its workspace uses
`AssetSource.artifact("plugin-under-test")`, the helper registers the supplied plugin JAR under that name:

```java
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.launcher.AnvilLauncher;

import java.nio.file.Path;
import java.time.Duration;

public final class EmbeddedScenario {
	public static void run(AnvilScenario scenario, Path pluginJar) {
		var options = EngineOptions.builder()
				.eulaAccepted(true)
				.protocolId("mcprotocol")
				.workDirectory(Path.of("build", "anvil"))
				.keepFailedWorkspaces(true)
				.stopTimeout(Duration.ofSeconds(30))
				.artifact("plugin-under-test", pluginJar)
				.build();

		try (ScenarioEngine engine = AnvilLauncher.create(options);
		     ScenarioContext context = engine.start(scenario)) {
			var entrypoint = context.processes().get(scenario.getEntrypoint());
			System.out.println("Ready at " + entrypoint.address());
			// Perform application actions and assertions while the context is open.
		}
	}
}
```

Call `EmbeddedScenario.run(scenario, pluginJar)` from your application with the scenario and an existing
plugin JAR. The helper returns after closing the environment. To interact with it, add the actions
inside its open context.

`start()` returns after processes and agents are ready and the setup hook has completed. Closing a
context releases its players and processes. Closing the engine also releases any remaining contexts
and the shared protocol backend. Unexpected exceptions should retain their original cause; cleanup
can add suppressed failures.

## Configure other entry points

`EngineProperties.fromSystemProperties()` or `EngineProperties.from(properties)` decodes the
[JVM property contract](../../building-blocks/environments/configuration/engine/index.md) into `EngineOptions`.
Passing explicit options to the launcher uses those options directly.

For a terminal application, construct `AnvilRunner` with its input reader and output writer and call
`run(arguments, options)`; add the `tooling-runner` artifact at the matching version. The
[manual environment guide](../../workflows/scenarios/running/index.md) covers catalog selection and commands.
For container execution, supply a configured
[`DockerExecutionProvider`](../../building-blocks/environments/configuration/execution/index.md) to the launcher.
