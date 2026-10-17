---
title: Capabilities
description: Understand the typed actions and observations owned by players and running processes.
---

A capability groups related behavior behind a Java interface. Retrieve it from the owner whose
behavior you want to control: a simulated player or a running server or proxy.

| Owner | Capability contract | Examples |
|---|---|---|
| Player | `PlayerCapability` | `Session` controls connections; `Messages` sends chat and observes replies |
| Process | `ProcessCapability` | `Console` dispatches native server or proxy commands; `Players` reports who is on the process |

Both extend the shared `Capability` contract. Ownership determines which object exposes the
capability and how long its instance lives.

## Choose the owner by who can normally do or know it

A player capability is the client side: what a Minecraft client can normally do or see, such as
connecting, sending chat, reading its inventory, or answering a dialog. A process capability is the
server side: what a server or proxy can normally do or know, such as running a console command or
knowing which players are on it and under which profile. A proxy counts as server side.

The implementation never decides the owner. A player capability can use packets or agents, and a
process capability can use an agent or another implementation. `Players` is built from the joins and
leaves a process's platform agent reports, and it belongs to that process because the process is what
knows who is on it. Alice's client knows only the profile it was granted; which backend a proxy sent her
to is something the proxy knows, so you ask the proxy's `Players`.

Use `Messages` to send a command as a player and `Console` to dispatch it as the process console.
The two callers can have different permissions and trigger different application behavior.

## Request behavior by its public type

`alice.capability(Messages.class)` retrieves Alice's message capability. The interface describes the
operation, while an installed provider implements it for the selected runtime. Tests use the public
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

To wait until a player arrives somewhere, watch that process's `Players` before the action and wait
for the join; see [watching players on a process](../../../building-blocks/environments/actions/players/index.md).

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
