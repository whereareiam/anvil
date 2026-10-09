---
title: Players
description: Understand the native clients that perform a test journey and the lifetime of each connection.
---

A player is a Minecraft protocol client controlled by your Java code. Create one when the behavior
under test needs a player to log in, send a command, receive a message, interact with the world, or
travel through a proxy. Creating several players lets a journey exercise behavior involving more
than one participant.

The scenario owns the players it creates. Each has a configured name, a native client version, and a
connection target. Its [capabilities](../capabilities/index.md) supply the actions and observations
available to the journey. Anvil does not render a game window or decide what the player should do;
your test supplies that sequence.

## Creation and connection are separate

`anvil.players().create("Alice")` registers a disconnected player. By default, it uses offline
authentication, the scenario's entrypoint, and a supported native version compatible with the servers
reachable from that target. Retrieve `Session` to begin login, then wait for the connection result.

This distinction lets you obtain capabilities before login, create another player only when a later
step needs one, or test what happens when a server rejects the connection. A player's name must be
unique among the players currently registered in that context.

## Example: connect Alice and read the observed identity

The example uses two player-scoped capabilities: `Session` controls login, and `Server` observes
agent-reported identity and routes. For now, treat them as the player's connection and observation
interfaces. The next concept explains how capability retrieval works.

The helper below receives an already running `ScenarioContext anvil`. Assume its entrypoint is a
direct Paper process named `server`, no player named Alice exists yet, and the runtime supplies
`Session` and `Server`. A test or embedding application calls this helper after starting the scenario.
The [first test](../../first-test/index.mdx) supplies a complete environment with those names.

Place `PlayerExample.java` in package `com.example.test` alongside the code that calls it.
Change the package declaration if your project uses a different package.

```java
package com.example.test;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;

final class PlayerExample {
	static PlayerIdentity connectAlice(ScenarioContext anvil) {
		var alice = anvil.players().create("Alice");
		try {
			var session = alice.capability(Session.class);
			session.connect();
			session.connected();
			return alice.capability(Server.class).joined("server");
		} finally {
			alice.destroy();
		}
	}
}
```

The method returns after login and a matching agent-observed route, then releases Alice. In this
direct-server example, the server agent supplies the observation. In a proxy topology, `Server.joined`
can also match the proxy's report of its connected backend. Use a backend-specific agent operation
when you need independent proof from that backend.

The returned identity is a snapshot. A caller can inspect `getObservedUsername()` or
`getRoute().getServer()`; neither result proves that application data or permissions are correct.

## One player can have several sessions

Disconnecting ends the current network session while retaining the player. Rejoining starts a fresh
session with its configured target. Destroying the player permanently releases it and unregisters
its name; a replacement player is a new object with new capability instances.

The example destroys Alice explicitly because its short journey is complete. Longer tests can keep
her for later steps, and scenario teardown releases players that remain. Never carry a player into
another scenario execution.

For detailed options, see [creating and managing players](../../../building-blocks/players/connections/index.md)
and [Session](../../../building-blocks/players/capabilities/session/index.md) for connection behavior.

Next: [Capabilities](../capabilities/index.md) explains the typed actions and observations you
retrieve from each player.
