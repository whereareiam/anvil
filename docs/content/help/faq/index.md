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

It speaks the native protocol through the selected provider. It does not render the game or
supply autonomous movement, pathfinding, or crafting. Installed [capabilities](../../building-blocks/players/capabilities/index.md)
provide the actions your test can request.

## Does the umbrella plugin install every runtime?

It supplies the JUnit/foreground integrations and built-in capabilities. Platform units remain
explicit, and the protocol provider must be installed. See [Plugins and dependencies](../../integrations/gradle/plugins/index.md).

## Can one player visit servers on different native versions?

Every server reachable through its connection target must be compatible with its selected native
client. Anvil rejects incompatible routes and does not silently install protocol translation.
Consult the [compatibility page](../../building-blocks/environments/platforms/versions/index.md).

## Can I join the environment myself?

Yes. Use a catalog and the [manual runner](../../workflows/scenarios/running/index.md).
Joining from another machine requires the scenario's explicit LAN exposure configuration.

## Can Anvil use Docker or a remote host?

The standard workflow uses local JVM processes. The Docker provider supports local-daemon execution
with explicit Java image mappings and has its own networking rules. It is not a generic SSH or
hosted orchestration system. Read [Execution providers](../../building-blocks/environments/configuration/execution/index.md).

## Does a reconnect or restart prove my plugin's state survived?

It proves the connection or lifecycle transition you observed. Verify the plugin's data through
its commands, messages, or a typed agent channelOperation after that transition. See
[Restarting processes](../../building-blocks/environments/actions/restarts/index.md).

## Why did a failed test remove its workspace?

A JUnit test-body assertion alone does not currently mark the scenario lifecycle unsuccessful.
Use a persistent workspace for that diagnostic workflow and read [Cleanup](../troubleshooting/cleanup/index.md).

## Can I use the framework without Gradle?

Yes. Use the launcher with explicit runtime dependencies and own the engine and context lifetimes.
The [embedding guide](../../integrations/embedding/index.md) shows the setup.

## Do I need to fork Anvil to add player behavior?

External libraries can supply capabilities, protocol backends, and agent operations. Choose the
appropriate [extension boundary](../../extending/index.md), publish its artifacts, and install them
in the consumer runtime.
