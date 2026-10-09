---
title: Scenario definitions
description: Declare independently discoverable Anvil environments and their presentation metadata.
---

An Anvil scenario definition owns one complete environment. Put each definition in the source set
used by the Anvil Gradle plugin, usually `src/anvil/java`:

```java
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;

public final class LocalPaperScenario implements AnvilScenarioDefinition {
	@Override
	public AnvilScenario define() {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.build();
		return AnvilScenario.builder()
				.name("local-paper")
				.metadata(PresentationMetadata.builder()
						.displayName("Local Paper")
						.description("A direct Paper environment for interactive checks.")
						.category("Development")
						.tag("paper")
						.build())
				.entrypoint(server.getName())
				.server(server)
				.manual(true)
				.build();
	}
}
```

The Gradle integration indexes compiled `AnvilScenarioDefinition` classes automatically. IntelliJ
and the foreground runner load definitions after preparation in a separate JVM; adding a definition
does not require a separate registration step. A module may contain as many definitions as it needs.
The module is their namespace, and a definition class is the stable source identity.

`PresentationMetadata` belongs to the returned scenario. Use it for a display name, description,
category, and tags. Server and proxy declarations accept the same metadata for their role labels.
These values affect presentation and filtering only; technical names remain the execution identity.

Inspect and start definitions with:

```shell
./gradlew anvilScenario --list
./gradlew anvilScenario --scenario=local-paper
./gradlew anvilScenario --definition=com.example.test.LocalPaperScenario
```

Use `--definition` when two definitions return the same scenario name. A full scenario start
prepares and starts every declared server and proxy in dependency order, then runs its setup hook.
Starting a named process is a partial operation and leaves the other processes stopped.

JUnit can select the same definition directly with `@AnvilTest`. The definition is evaluated once
for the selected workflow, and each test method receives a fresh scenario execution.

For generated compatibility matrices, create one definition class per matrix entry or generate the
definition index during the build. Keep matrix generation out of the normal scenario discovery API.
