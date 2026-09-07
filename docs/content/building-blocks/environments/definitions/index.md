---
title: Definition
description: Declare a repeatable environment and prepare its live state before a test or manual session begins.
---

An environment definition describes the starting point that an automated test or a prepared scenario
needs. Declare its servers, proxies, and file inputs first. Add a setup hook when the starting point
also requires work inside the running environment, such as connecting a participant or issuing an
application setup command.

The definition returns an `AnvilScenario`; creating that declaration does not start it. The engine
prepares the inputs and runs the setup hook when a workflow starts the environment.

## Define the starting point

This example prepares one Paper server and a connected player named `GuideBot`. A test can use that
player in its journey, or a human can join a manual variant with the participant already present.

Use the dependencies from [Installation](../../../getting-started/installation/index.md): Paper,
MCProtocol, and the Session and Server capabilities, which the umbrella plugin includes. Save the
class as `com/example/test/PreparedEnvironment.java` in the source set used by your integration. With
the Anvil Gradle plugin, the complete path is `src/anvil/java/com/example/test/PreparedEnvironment.java`.

```java
package com.example.test;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

public final class PreparedEnvironment implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.build();

		return AnvilScenario.builder()
				.name("prepared-paper")
				.entrypoint(server.getName())
				.server(server)
				.setupHook(access -> {
					var guide = access.players().create("GuideBot");
					var session = guide.capability(Session.class);
					session.connect();
					session.connected();
					guide.capability(Server.class).joined(server.getName());
				})
				.build();
	}
}
```

`prepared-paper` identifies the environment; `server` identifies the process within it. The entrypoint
selects the default connection target for newly created players. Use distinct process names and
select the proxy as the entrypoint when the behavior must pass through a proxy.

The setup waits establish a native connection and agent-observed presence on this direct server.
They make GuideBot part of the starting state; they do not verify your plugin's behavior. A journey
can retrieve the existing player with `anvil.players().get("GuideBot")`, or create another player with
a different name. An environment that needs only processes can omit the setup hook.

## Keep inputs in the declaration

Choose the [platform](../platforms/index.md) required by the behavior, then
[provision its executable](../provisioning/platform/index.md) and a compatible [Java runtime](../provisioning/java/index.md).
Automated runs use an explicit provider build or a supported content checksum. A supplied local or
named server JAR also declares `minecraftVersion`, so Anvil can validate compatible native clients.

Use a [workspace plan](../workspaces/index.md) for the packaged plugin, configuration files, and fixture
data. Register the exact artifact through the integration or embedding application rather than
selecting an unrelated file from another project's build directory. Files are prepared before
platform configuration and process startup; they do not need a live setup hook.

Use the hook for operations that require ready processes or players. Keep the actions and assertions
that define the actual test result in the [testing journey](../../../workflows/testing/journeys/index.md).
This lets several tests reuse the same prepared state while checking different behavior.

## Prepare live state

When the environment starts, Anvil validates its declaration, prepares its inputs, starts servers
before proxies, and establishes process and agent readiness. It then invokes the setup hook. The
caller receives a ready environment only after that hook completes.

The callback receives `ScenarioAccess`, exposing the declaration, process lookup, and player manager.
It does not receive a `ScenarioContext` to close. The engine and the caller's integration own that
lifetime; see [Running context](../../../getting-started/concepts/context/index.md).

Wait for the precondition your hook creates. The example uses Session and Server waits; a hook that
sends console commands should use [bounded console observations](../actions/console/index.md).
Submitting a command alone does not establish that the requested setup finished.

If setup fails, startup fails rather than returning a ready context. Anvil attempts cleanup of the
resources it acquired and preserves the original failure with cleanup failures attached. Use
[Startup troubleshooting](../../../help/troubleshooting/startup/index.md) to inspect the cause and logs.

The hook runs once for each whole-environment startup. A [single-process restart](../actions/restarts/index.md)
does not rerun it. Starting the declaration again, including through the manual runner's whole-scenario
restart, creates a new run and repeats setup. Retained files follow the [workspace policy](../workspaces/index.md);
retaining a directory does not retain its previous player objects.

## Use the definition in either workflow

For an automated test, select `PreparedEnvironment.class` with `@AnvilTest`. For example, change the
annotation in `PlayerJoinTest` from the [first test](../../../getting-started/first-test/index.mdx), then run:

```shell
./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'
```

Before that test method starts, GuideBot is already connected. The method can create Alice and check
its own results. JUnit's per-method creation and teardown are documented in
[scenario selection](../../../integrations/junit/selection/index.md).

For human testing, a catalog can derive a manual variant from the same definition. This fragment
belongs in `AnvilScenarioProvider.register(ScenarioRegistry registry)`, with `PreparedEnvironment` in
the same package and `AnvilScenario` imported as above:

```java
AnvilScenario manual = new PreparedEnvironment().define().toBuilder()
		.name("prepared-paper-manual")
		.manual(true)
		.build();
registry.scenario(manual);
```

Register the catalog as described in [Catalogs and groups](../../../workflows/scenarios/catalogs/index.md).
You can then launch `prepared-paper-manual` through the runner and join with your real client while
GuideBot remains present. Manual mode does not install missing components or accept the EULA; the
same declared dependencies and explicit runtime settings still apply.
