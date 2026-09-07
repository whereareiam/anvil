---
title: Capabilities
description: Understand the typed actions and observations attached to each simulated player.
---

A capability groups related player behavior behind a Java interface. A player exposes the
capabilities installed in its runtime, and your journey asks for the interface it needs. For example,
`Session` controls connections, `Messages` sends chat and observes replies, and `Inventory` works with
client-observed items and containers.

In the current API, all capability instances are player-scoped and their interfaces extend
`PlayerCapability`. An implementation can call a server or proxy agent, but this does not turn it into
a process-wide capability. Even `Server` describes a particular player's identity and route. General
process controls are accessed through the running context.

Use a capability when the behavior belongs to a player. Sending a chat command should use that
player's `Messages`; sending an administrative console command uses the running process's console.
These operations can have different permissions and trigger different application behavior.

## Request behavior by its public type

`alice.capability(Messages.class)` retrieves Alice's message capability. The interface describes the
operation, while an installed provider implements it for the selected runtime. Tests use the public
interface without importing protocol packets, platform SDKs, or transport code.

Installing an API alone does not supply its implementation. Anvil's bundled runtime offers several
built-in capabilities, and external libraries can provide more. The [capability guide](../../../building-blocks/players/capabilities/index.md)
lists the available units and their requirements. You will choose those dependencies during setup.

## Example: observe a message delivered to Alice

The helper below assumes `ScenarioContext anvil` is already running a Paper process named `server`,
and `SimulatedPlayer alice` belongs to that context and is connected to it. The runtime supplies
`Messages`. Call this from a journey after Alice has joined; the
[first test](../../first-test/index.mdx) shows the surrounding setup.

Place `CapabilityExample.java` in package `com.example.test` alongside the code that calls it.
Change the package declaration if your project uses a different package.

```java
package com.example.test;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;

import java.time.Duration;
import java.util.UUID;

final class CapabilityExample {
	static String receiveAnnouncement(ScenarioContext anvil, SimulatedPlayer alice) {
		Messages messages = alice.capability(Messages.class);
		String marker = "capability-check-" + UUID.randomUUID();
		anvil.processes().server("server").console().sendCommand("say " + marker);
		return messages.received(marker, Duration.ofSeconds(10));
	}
}
```

The console triggers the announcement, and Alice's capability waits for text delivered to her client.
Returning a matching message establishes delivery. It does not establish a plugin-specific result
unless that result is what the message represents.

The marker is unique because `Messages.received` searches captured history, including earlier
messages. Repeating an identical expected reply could match an older result. The bundled implementation
also retains message history across reconnects of the same player.

## Actions need the right observations

An action returning does not necessarily mean its server-side effect has finished. Pair it with the
appropriate bounded wait: login completion through `Session`, message delivery through `Messages`,
or an inventory condition through `Inventory`. Immediate methods such as `history()` return a
snapshot without waiting.

The `Server` capability observes aggregate identity and routes through platform agents. A route may
include a proxy's connected-server report, so it is distinct from inspecting native state directly
on a particular backend.

## Keep capabilities with their player

Capability instances belong to the player from which you retrieved them. Use new instances after
destroying and replacing that player, and stop using them when the scenario closes. If a required
capability is missing, retrieval fails with `CapabilityUnavailableException`; do not silently skip
the behavior your test promises to verify.

For more actions, see [using capabilities](../../../building-blocks/players/capabilities/index.md)
and [assertions and waits](../../../workflows/testing/assertions/index.md).

Next: [Installation](../../installation/index.md). You now have the concepts needed to configure
a project and put them together in the first test.
