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

## Example: connect Alice and read the profile the server sees

The example uses one capability of the player and one of the server. `Session` is Alice's: it controls
her client's login. `Players` is the server's: it reports who is on that server and under which profile.
For now, treat them as the client's connection interface and the server's view of its players. The next
concept explains how capability retrieval and ownership work.

The helper below receives an already running `ScenarioContext anvil`. Assume its entrypoint is a
direct Paper process named `server`, no player named Alice exists yet, and the runtime supplies
`Session` and `Players`. A test or embedding application calls this helper after starting the scenario.
The [first test](../../first-test/index.mdx) supplies a complete environment with those names.

Place `PlayerExample.java` in package `com.example.test` alongside the code that calls it.
Change the package declaration if your project uses a different package.

```java
package com.example.test;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;

final class PlayerExample {
	static PlayerProfile connectAlice(ScenarioContext anvil) {
		Players players = anvil.processes().server("server").capability(Players.class);
		PlayerWatch arrivals = players.watch();
		var alice = anvil.players().create("Alice");
		try {
			var session = alice.capability(Session.class);
			session.connect();
			session.connected();
			return arrivals.joined(alice).getProfile();
		} finally {
			alice.destroy();
		}
	}
}
```

The method returns once Alice has logged in and the server reports her join, then releases Alice. The
watch is created before the login, so it sees the join however quickly it happens. In a proxy topology,
watch the backend you expect her on in the same way. To check which backend the proxy connected her
to, watch the proxy's `Players` before the login and wait for `joined(alice, "lobby")` on that watch.

The returned profile is what the server reports for Alice. It does not prove that application data or
permissions are correct. The profiles the client itself asked for and was granted are on the player:
`alice.requestedProfile()` and `alice.grantedProfile()`.

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
