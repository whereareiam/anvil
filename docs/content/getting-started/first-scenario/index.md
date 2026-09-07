---
title: First scenario
description: Define a pinned Paper environment, install your plugin JAR, and exercise it from JUnit.
---

# Run your first scenario

This example starts one Paper server, installs the JAR produced by the current project, creates a
native-protocol player, and checks that the server observes the player's login. Put both classes
under `src/anvil/java/com/example/test/`.

The project must already have the setup from [Installation](../installation/index.md), including
the Paper platform unit, MCProtocol provider, registered artifact, and EULA acknowledgement.

## Define the environment

Create `PaperScenario.java`:

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

		return AnvilScenario.builder()
				.name("paper-plugin-test")
				.entrypoint(server.getName())
				.server(server)
				.build();
	}
}
```

`Distribution.remote` uses a provider build identifier. Keep it pinned for repeatable automated
runs. The `entrypoint` is the process where newly created players connect. Asset targets are
relative to the process workspace; file sources are copied to the target file.

## Write the journey

Create `PlayerJoinTest.java`:

```java
package com.example.test;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.junit.AnvilTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayerJoinTest {
	@Test
	@AnvilTest(PaperScenario.class)
	void observesAliceOnTheServer(ScenarioContext anvil) {
		SimulatedPlayer alice = anvil.players().create("Alice");
		Session session = alice.capability(Session.class);
		session.connect();
		session.connected();

		Server server = alice.capability(Server.class);
		assertEquals("Alice", server.joined("server").getObservedUsername());
	}
}
```

`@AnvilTest` starts a fresh scenario around the test method and injects its
`ScenarioContext`. `Session.connect()` starts the login sequence; `connected()` waits up to the
default ten seconds. `Server.joined("server")` waits until the named backend observes the player.

## Run the test

```shell
./gradlew anvilTest
```

Anvil builds the registered artifact, prepares the server workspace, starts the process, runs the
journey, and releases the player and process afterward. If the run fails, Anvil retains the
diagnostic workspace according to its engine options and includes the bounded process output in
the failure path. Inspect the retained `anvil-console.log` when startup or routing fails.

To include live journeys in the normal test lifecycle, use:

```shell
./gradlew test -Panvil.testMode=full
```

Keep this opt-in in local and CI workflows because it starts a real Minecraft process. Use ordinary
`./gradlew test` for unit tests that do not need a live server.

## What this test proves

The example proves that the packaged artifact can be installed into the selected Paper workspace,
that the selected native client can complete login, and that the server observes the expected
player name. Add the `Messages`, `Movement`, `Inventory`, or `Interaction` capability when the
journey needs to drive or observe those behaviors. See [Writing tests](../../writing-tests/index.md)
for capability and workspace workflows.
