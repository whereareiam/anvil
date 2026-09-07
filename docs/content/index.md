---
title: Overview
description: Write repeatable tests for packaged plugins using real servers, proxies, and protocol players.
---

Anvil is a Java testing framework for developers building Minecraft plugins and integrations.
It starts the servers and proxies your test needs, installs your packaged artifacts, and gives
your code control of players that speak the native Java Edition protocol.

A scenario describes the environment once. Use it in a JUnit test to verify behavior after a
change, or run it in the foreground when you need to join and inspect the environment yourself.

## Start with a real behavior

A useful Anvil test might verify that a command produces the expected message, that a player
rejoins after a kick, or that a proxy sends an authenticated identity to the intended backend.
The test supplies the actions and assertions. Anvil manages provisioning, process readiness,
player connections, and cleanup around those actions.

Keep isolated business logic in ordinary unit tests. Use a real environment when the result
depends on platform behavior, the packaged plugin, protocol traffic, or interactions between processes.

## How a test fits together

```text
Scenario declaration
  ├─ Pinned server and proxy distributions
  ├─ Plugin JARs, configuration, and workspace rules
  └─ Connection topology
       ↓
Running scenario context
  ├─ Processes: console, addresses, and restarts
  └─ Players: actions and observations through capabilities
       ↓
Your test: perform an action, wait for an observation, assert the result
       ↓
Close the context: release players and stop the managed processes
```

Players are created as the test needs them. Installed capabilities provide session management,
messages, movement, inventory, interactions, and server observations. You can add capabilities
or implement another backend through Anvil's public extension contracts.

## Follow the guide

Start with [Concepts](./getting-started/concepts/index.md), [Installation](./getting-started/installation/index.md),
and [First test](./getting-started/first-test/index.mdx). Then use the documentation in three layers:

1. **[Building blocks](./building-blocks/index.md):** prepare servers, proxies, files, and runtime
   settings under Environments; create and control native clients under Players.
2. **[Workflows](./workflows/index.md):** choose Testing for automated actions and assertions, or
   Scenarios for a prepared environment you launch and join with a real client.
3. **[Integrations](./integrations/index.md):** connect either workflow through JUnit, Gradle, or an
   application that embeds Anvil.

The two workflows share their environment declarations and can both include simulated players.
A human-run scenario can help explore a behavior before you turn its checks into an automated test.

## Add behavior or get help

| You need to… | Go to |
|---|---|
| Look up engine properties and defaults | [Engine options](./building-blocks/environments/configuration/engine/index.md) |
| Diagnose a failing run or report a defect | [Troubleshooting](./help/troubleshooting/index.md) |
| Add a capability, agent operation, backend, or platform | [Extensions](./extending/index.md) |
| Change Anvil's implementation or documentation | [Contributing](./contributing/index.md) |

Option tables and API links live beside the guides that use them. Extension and contributor guides
assume you already have a working consumer environment.

## Supported environment

Anvil targets Java 21. Managed processes may need a newer Java runtime; the
[Java guide](./building-blocks/environments/provisioning/java/index.md) explains selection and provisioning.
The bundled platforms are Paper and Spigot servers, plus Velocity and BungeeCord proxies.
MCProtocolLib supplies the bundled native player backend. See the
[version and route matrix](./building-blocks/environments/platforms/versions/index.md) for tested combinations.

Local process execution is the standard Gradle workflow. A Docker execution provider is also
available with explicit image configuration; read its [setup and limits](./building-blocks/environments/configuration/execution/index.md)
before selecting it. Kubernetes, SSH/hosted orchestration, Fabric, Sponge, and Bedrock are outside
the current scope. Protocol players do not render the game or provide autonomous AI, pathfinding,
or crafting automation.
