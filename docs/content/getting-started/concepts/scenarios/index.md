---
title: Scenarios
description: Describe the complete environment a journey needs and reuse that declaration across runs.
---

A scenario is the description of a test environment: the servers and proxies it contains, their
inputs, and the default process that players connect to. It answers “what needs to be running for
this test?” Your journey supplies the actions and assertions after that environment is ready.

For a welcome-message plugin, one server may be enough. To test a proxy transfer, the scenario can
contain a proxy and two backend servers. Put the processes involved in the behavior into the same
scenario so Anvil can prepare their connections and manage their lifetimes together.

## Describe one server

A scenario definition is a Java class implementing `AnvilScenarioDefinition`. Its `define()` method
returns the environment declaration. The [first test](../../first-test/index.mdx) provides the full
class; here, read the method body to see how the declaration fits together.

The example uses a process named `server` and a pinned Paper build. Add these imports to the
definition class when putting it into a project:

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.Platforms;
```

```java
MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.PAPER)
		.distribution(Distribution.remote("1.21.11", "132"))
		.build();

return AnvilScenario.builder()
		.name("welcome-message")
		.entrypoint("server")
		.server(server)
		.build();
```

The result describes one process. `server` identifies it in lookups and player routes;
`welcome-message` identifies the scenario. These names are not public hostnames.
The `entrypoint` names the default connection target for players created later.

Calling a builder's `build()` creates a Java value. It does not download Paper, allocate a running
player, or launch the server. The engine performs that work when the scenario is started. The
[platform](../platforms/index.md), [distribution](../distributions/index.md), and
[workspace](../workspaces/index.md) concepts explain the remaining inputs.

## Reuse the environment

A single scenario definition can serve several journeys: a first join, a rejected command, or a
reconnect. Each test can create the players and perform the actions it needs. In the JUnit
integration, selecting the same definition for multiple methods still starts a fresh scenario
execution for each method; it does not share a running server automatically.

Create another definition when the environment changes, such as a different platform or a proxy
topology. A named catalog can also expose definitions to the manual runner. Use
`AnvilScenarioDefinition` for a type-selected environment and `AnvilScenarioProvider` for a catalog;
[scenario guides](../../../building-blocks/environments/index.md) explain both entry points.

## Decide what belongs here

Put process versions, plugin assets, topology, and common preparation in the scenario. Put the
behavior under test in the journey. A setup hook can prepare state after startup, but an assertion
about the action you are testing is easier to understand beside that action in the test method.

Next: [Platforms](../platforms/index.md) explains how to choose the server or proxy implementation
for each part of this scenario.
