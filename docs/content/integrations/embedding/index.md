---
title: Embedding Anvil
description: Start scenarios from a Java application with explicit runtime dependencies and engine ownership.
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
platform, protocol library, or capability set for your application. Preserve service
descriptors if you assemble another shaded JAR around these dependencies.

## Start a supplied scenario

Put this helper at `src/main/java/EmbeddedScenario.java` in your application. Pass an `AnvilScenario` containing
an entrypoint and any desired processes. If its workspace uses
`AssetSource.artifact("plugin-under-test")`, the helper registers the supplied plugin JAR under that name:

```java
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
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
				.protocolLibrary("mcprotocol")
				.workDirectory(Path.of("build", "anvil"))
				.keepFailedWorkspaces(true)
				.processTimeouts(ProcessTimeouts.builder().shutdown(Duration.ofSeconds(30)).build())
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
context releases its players and processes. Closing the returned engine also releases remaining
contexts, the protocol libraries it created, and artifact acquisition resources. Unexpected exceptions
should retain their original cause; cleanup can add suppressed failures.

Use `context.finish(false)` when your application catches a failed journey and needs to report that
outcome to cleanup policy. The default `close()` reports normal completion. To install global
diagnostics or per-scenario attachments, use `AnvilLauncher.builder()` and add an
[engine extension](../../extending/engine/index.md).

## Prepare before starting individual processes

When your application chooses which components to start, use `engine.prepare(scenario)` to obtain
a `ScenarioContext`. The launcher prepares the complete topology, routes, and process inputs while
leaving JVMs stopped. Call `context.processes().start(name)` for an individual component and
`context.start()` for the remaining processes plus global extensions and scenario setup. The
[individual-start example](../../building-blocks/environments/actions/restarts/index.md#start-and-stop-individual-components)
shows this sequence and its cleanup.

The context owns unstarted resources too. Closing either the context or its engine releases them.
Every assembly supplies this lifecycle through `ScenarioFactory.create(...)`, returning the
context before process startup. `engine.start(...)` is the convenience that prepares a context
and completes its `start()` before handing it to the caller.

## Configure other entry points

`EngineProperties.fromSystemProperties()` or `EngineProperties.from(properties)` decodes the
[JVM property contract](../../building-blocks/environments/configuration/engine/index.md) into `EngineOptions`.
Passing explicit options to the launcher uses those options directly.

For a terminal application, add `me.whereareiam.anvil:tooling-runner` at the matching version.
Supply a `Supplier<ScenarioEngine>` that creates an engine owned by the runner, together with the
application's input reader and output writer:

```java
import me.whereareiam.anvil.runner.AnvilRunner;

// input is a Reader, output is a PrintWriter, and engineFactory creates a ScenarioEngine.
new AnvilRunner(input, output, engineFactory).run(arguments);
```

The runner acquires an engine only when a scenario starts and closes it when the session ends.
Listing scenario definitions does not create an engine. The runner library does not include the default launcher;
your application supplies its engine assembly. `RunnerSession` accepts the same engine-factory boundary
for applications serving structured tooling instead of a terminal shell.

For a ready-to-run process using the default engine, add `me.whereareiam.anvil:tooling-launcher`.
Its `me.whereareiam.anvil.tooling.launcher.AnvilCli` entry point binds standard streams and decodes
engine properties. `me.whereareiam.anvil.tooling.launcher.AnvilTooling` serves the structured protocol.
The Gradle integration selects this executable assembly automatically. The
[manual environment guide](../../workflows/scenarios/running/index.md) covers scenario selection and commands.
For container execution, supply a configured
[`DockerExecutionProvider`](../../building-blocks/environments/configuration/execution/index.md) to the launcher.
