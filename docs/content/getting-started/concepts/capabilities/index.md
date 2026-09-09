---
title: Capabilities
description: Understand the typed actions and observations owned by players and running processes.
---

A capability groups related behavior behind a Java interface. Retrieve it from the owner whose
behavior you want to control: a simulated player or a running server or proxy.

| Owner | Capability contract | Examples |
|---|---|---|
| Player | `PlayerCapability` | `Session` controls connections; `Messages` sends chat and observes replies |
| Process | `ProcessCapability` | `Console` dispatches native server or proxy commands |

Both extend the shared `Capability` contract. Ownership determines which object exposes the
capability and how long its instance lives. It does not determine the implementation mechanism:
a player capability can use packets or agents, and a process capability can use an agent or
another implementation. For example, `Server` uses agents to observe one player's identity and
route, so it still belongs to that player.

Use `Messages` to send a command as a player and `Console` to dispatch it as the process console.
The two callers can have different permissions and trigger different application behavior.

## Request behavior by its public type

`alice.capability(Messages.class)` retrieves Alice's message capability. The interface describes the
channelOperation, while an installed provider implements it for the selected runtime. Tests use the public
interface without importing protocol packets, platform SDKs, or transport code.

For a process, use `process.capability(Console.class)`. This does not require creating a player.
The installed implementation determines whether a capability is available; Anvil's built-in
`Console` requires the process's platform agent.

Installing an API alone does not supply its implementation. Anvil's bundled runtime offers
[player capabilities](../../../building-blocks/players/capabilities/index.md) and
[agent-backed process capabilities](../../../building-blocks/environments/actions/agents/index.md); external
libraries can provide more. You will choose those dependencies during setup.

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

## Keep capabilities with their owner

Player capability instances belong to the player from which you retrieved them. Use new instances
after destroying and replacing that player. Process capability instances belong to the logical
scenario process and remain the same across JVM restarts. Agent-backed requests can fail while
the replacement connection is unavailable.

After the scenario closes, capability lookup fails and `hasCapability(...)` returns `false`.
Stop using previously retrieved capabilities at that point.

If a required capability is missing, retrieval fails with `CapabilityUnavailableException`; do not
silently skip the behavior your test promises to verify.

For more actions, see [using capabilities](../../../building-blocks/players/capabilities/index.md)
and [assertions and waits](../../../workflows/testing/assertions/index.md).

Next: [Installation](../../installation/index.md). You now have the concepts needed to configure
a project and put them together in the first test.
