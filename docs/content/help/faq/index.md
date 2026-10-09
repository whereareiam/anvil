---
title: FAQ
description: Common decisions about test scope, players, platforms, execution, and state.
---

Find quick answers to common choices below. For a failing scenario, use
[Troubleshooting](../troubleshooting/index.md). To report a reproducible framework defect, follow
[Reporting issues](../reporting-issues/index.md).

## When should I use Anvil?

Use Anvil when a result depends on a real server, proxy, native session, or packaged plugin.
Keep isolated logic in ordinary unit tests. The [first test](../../getting-started/first-test/index.mdx)
shows a small environment before adding plugin-specific assertions.

## Is a simulated player a full game client?

It speaks the native protocol through the selected protocol library. It does not render the game or
supply autonomous movement, pathfinding, or crafting. Installed [capabilities](../../building-blocks/players/capabilities/index.md)
provide the actions your test can request.

## Does the Anvil plugin install every runtime?

The standard plugin supplies scenario execution and IDE discovery. Add the JUnit plugin for
automated tests. Platform providers, capabilities, and protocol libraries are installed explicitly. See [Plugins and dependencies](../../integrations/gradle/plugins/index.md).

## Which Minecraft versions can I test?

Players speak Minecraft 1.18.2, 1.21.1, 1.21.11, and 26.1.2 through
MCProtocolLib, plus the other versions each release lists. Anvil's live matrix runs each of those
four versions on Paper, 1.21.1 and newer on NeoForge, and 1.21.11 and 26.1.2 also on Spigot and behind
Velocity and BungeeCord. A
newer version can run with your own release data. See
[Versions and compatibility](../../building-blocks/environments/platforms/versions/index.md).

## Can one player visit servers on different native versions?

Every server reachable through its connection target must be compatible with its selected native
client. Anvil rejects incompatible routes and does not silently install protocol translation.
Consult the [compatibility page](../../building-blocks/environments/platforms/versions/index.md).

## Can I join the environment myself?

Yes. Use a scenario definition and the [manual runner](../../workflows/scenarios/running/index.md).
Joining from another machine requires the scenario's explicit LAN exposure configuration.

## Can Anvil use Docker or a remote host?

The standard workflow uses local JVM processes. The Docker provider supports local-daemon execution
with explicit Java image mappings and has its own networking rules. It is not a generic SSH or
hosted orchestration system. Read [Execution providers](../../building-blocks/environments/configuration/execution/index.md).

## Does a reconnect or restart prove my plugin's state survived?

It proves the connection or lifecycle transition you observed. Verify the plugin's data through
its commands, messages, or a typed agent operation after that transition. See
[Restarting processes](../../building-blocks/environments/actions/restarts/index.md).

## Why did a failed test remove its workspace?

A JUnit test-body assertion alone does not currently mark the scenario lifecycle unsuccessful.
Use a persistent workspace for that diagnostic workflow and read [Cleanup](../troubleshooting/cleanup/index.md).

## Can I use the framework without Gradle?

Yes. Use the launcher with explicit runtime dependencies and own the engine and context lifetimes.
The [embedding guide](../../integrations/embedding/index.md) shows the setup.

## Do I need to fork Anvil to add player behavior?

External libraries can supply capabilities, protocol libraries, and agent operations. Choose the
appropriate [extension boundary](../../extending/index.md), publish its artifacts, and install them
in the consumer runtime.
