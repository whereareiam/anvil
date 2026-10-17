---
title: Watching players on a process
description: Wait until a player is on a server, read the profile a server or proxy sees, and find a player's backend.
---

Use a process's `Players` capability when a test needs to know what a server or proxy knows about the
players on it: whether a player is on it now, under which profile, and on a proxy, which backend it
connected the player to. Retrieve it from the process, not from the player:
`anvil.processes().server("lobby").capability(Players.class)`.

`Players` reads only the joins and leaves the platform agent of that one process reports. Paper,
Spigot, NeoForge, Velocity, and BungeeCord agents report them; nothing has to be declared in the
scenario. Install the `me.whereareiam.anvil.capability.players` unit with the platform units, or
explicitly select the default capability unit, which includes it.

## Wait until a player is on a server

Create the watch before the action that moves the player, then wait on it. A watch follows joins and
leaves from the moment it was created, so a join that happens before the test reaches the wait is
still found.

This helper expects the [proxy environment](../../platforms/proxies/index.md): a proxy named `proxy`
as the entrypoint and a backend named `lobby`. Place it at
`src/anvil/java/com/example/test/LobbyChecks.java` and call `LobbyChecks.verify(anvil)` from a test
in the same package receiving `ScenarioContext anvil`:

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.players.model.PlayerVisit;
import me.whereareiam.anvil.capability.session.Session;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LobbyChecks {
	public static void verify(ScenarioContext anvil) {
		Players lobby = anvil.processes().server("lobby").capability(Players.class);
		Players proxy = anvil.processes().proxy("proxy").capability(Players.class);
		PlayerWatch arrivals = lobby.watch();
		PlayerWatch moves = proxy.watch();

		var alice = anvil.players().create("Alice");
		var session = alice.capability(Session.class);
		session.connect();
		session.connected();

		PlayerVisit visit = arrivals.joined(alice);
		assertEquals("Alice", visit.getProfile().getUsername());
		assertEquals("Alice", lobby.profile(alice).orElseThrow().getUsername());

		moves.joined(alice, "lobby");
		assertEquals(Optional.of("lobby"), proxy.backend(alice));
	}
}
```

Run the containing test with `./gradlew anvilTest --tests 'your.package.YourTest'`.
`connected()` proves that the client logged in to the proxy. `arrivals.joined(alice)` proves that the
lobby's own agent reported Alice joining the lobby. `moves.joined(alice, "lobby")` proves that the
proxy reported connecting her to `lobby`; once that wait has returned, `proxy.backend(alice)` names the
same backend. Neither proves that your plugin's data or permissions are correct; assert those
separately.

`joined(player)` and `joined(player, backend)` wait for the player's default timeout of ten seconds.
Pass a `Duration` as the last argument for another bound.

## Read what a process knows now

These calls return at once, without waiting:

| Call | Result |
| --- | --- |
| `online()` | Every player on the process now as a `PlayerVisit`, oldest join first |
| `profile(player)` | The profile this process reports for the player while the player is on it; empty while not on it |
| `backend(player)` | On a proxy, the scenario name of the backend the proxy connected the player to and has not seen it leave; empty on a server, and while the proxy has connected the player to none. A snapshot; see below |
| `watch()` | A new `PlayerWatch` that follows joins and leaves of this process from now on |

`backend(player)` reports only what the proxy has reported so far, and the proxy's report can arrive a
moment after the backend's own report of the same join. Read it straight after an action, or straight
after a backend's `joined(player)`, and it can still name the previous backend. To wait for a move,
wait on the proxy's watch with `joined(player, backend)`; read `backend(player)` only once that wait
has returned or when nothing is moving the player.

A `PlayerVisit` describes one stay of a player on the process: `getProfile()` as the process reports
it, `getJoinedAt()`, and `getLeftAt()`, which is empty while the player is still there.

`profile(player)` can differ from the profile the client was granted. A backend sees what the proxy
forwarded, and a plugin may change the name or UUID a process uses. Compare the two when testing that
behavior: `alice.grantedProfile()` is what the client received in its login success, and
`lobby.profile(alice)` is what the lobby reports.

## Follow a player through a proxy

A proxy knows a player from the moment it logs in to the proxy, also before the proxy connects it to
any backend, for example while a login plugin holds the player on the loading screen. Its watch reports
`left` when the player disconnects from the proxy.

On a proxy, `joined(player)` and `left(player)` without a backend mean the player connecting to and
disconnecting from the proxy itself. A switch between backends does not end that stay. To follow
backends, pass the backend's scenario name:

| Wait | Meaning on a proxy's watch |
| --- | --- |
| `joined(player, backend)` | The proxy next connects the player to that backend; the visit is the player's stay on the backend as the proxy reports it |
| `left(player, backend)` | The proxy next moves the player away from that backend, by connecting it to another or by the player disconnecting |
| `neverJoined(player, backend, duration)` | The proxy does not connect the player to that backend for the duration |
| `expectJoined(player, backend)` | An expectation for `player.awaitFirst(...)`, met once the proxy has connected the player to that backend |

Each takes an optional `Duration` as its last argument, like the waits without a backend. On a server's
watch the backend variants never match, because a server connects players to no backend.

To check a switch from `lobby` to `game`, create the proxy's watch before the action that moves the
player. In this fragment `session` and `messages` are Alice's Session and Messages, and the proxy
accepts `/server game`:

```java
PlayerWatch moves = anvil.processes().proxy("proxy").capability(Players.class).watch();
session.connect();
session.connected();
moves.joined(alice, "lobby");

messages.command("server game");
moves.left(alice, "lobby");
moves.joined(alice, "game");
```

The proxy's watch shows which backend the proxy chose. To prove that the backend itself received the
player, and with which profile, also watch that backend's own `Players`.

## Check every server a player passes through

When a player should pass through `auth` on its way to `lobby`, create a watch on each server before
the action, then wait on each:

```java
PlayerWatch auth = anvil.processes().server("auth").capability(Players.class).watch();
PlayerWatch lobby = anvil.processes().server("lobby").capability(Players.class).watch();

session.connect();
session.connected();

auth.joined(alice);
// Complete the login your plugin asks for here.
auth.left(alice);
lobby.joined(alice);
```

Each wait continues after the join or leave the previous wait on the same watch found. A brief stay on
`auth` is therefore still seen after the player has moved on, and a player that joins a process twice
is matched once per `joined` call. Each watch keeps its own position, so several watches on one process
do not affect each other.

`left(player)` waits until the player next leaves the process: on a server by moving to another server
or by disconnecting, on a proxy by disconnecting from it.

## Expect that a player never joins

To check that a refused login or command does not move a player, use `neverJoined`:

```java
PlayerWatch game = anvil.processes().server("game").capability(Players.class).watch();
messages.command("play");
game.neverJoined(alice, Duration.ofSeconds(3));
```

It fails as soon as the player has joined since the previous wait on that watch, however briefly it
stayed, and returns normally once the duration has passed without such a join. It does not move the
watch, so a later `joined(alice)` on it can still find a join that happens afterwards. Import
`java.time.Duration` for the bound.

On a proxy's watch, `neverJoined(alice, "game", Duration.ofSeconds(3))` checks that the proxy does not
send her to `game`.

When more than one outcome is correct, pass `watch.expectJoined(player)` or
`watch.expectJoined(player, backend)` to `player.awaitFirst(...)` together with other expectations; see
[assertions and waits](../../../../workflows/testing/assertions/index.md). Once `expectJoined(player)`
is met, `joined(player)` on the same watch returns that visit at once.

## Restarts and renamed players

The `Players` instance belongs to the logical process and is the same before and after a
[restart](../restarts/index.md), like `Dispatch`. A restarted process ends the visits it reported
before: after a restart, `online()` lists only players that joined the new generation, and a watch
created before the restart waits for the player to join again.

A player is found by every profile its client was granted at its logins, which
`alice.grantedProfiles()` returns. A player that a proxy or login plugin renamed, or gave another UUID,
is therefore still found, also after reconnecting with a different profile. Players are matched by
the UUIDs of those profiles, never by username, so players whose usernames differ only in letter case
are not confused with each other.

## When a wait fails

A wait that runs out names the player, the process, and who is on that process now:

```text
Player 'Alice' did not join 'auth' within PT10S; on 'auth': [Bob]
```

When nobody is on the process, the message ends with `nobody is on 'auth'`. Check, in this order:

- that the watch was created before the action, on the process you expect the player on;
- the player's session state, since a player that was kicked or never logged in reaches no server;
- on a proxy topology, whether the proxy chose another backend: wait on its watch with
  `joined(alice, backend)` for the backend you expect, or read `proxy.backend(alice)` once nothing is
  moving the player;
- the process's `anvil-console.log` for agent or plugin errors.

A backend wait on a proxy's watch names the proxy and the backend:

```text
'proxy' did not connect player 'Alice' to 'lobby' within PT10S; on 'proxy': [Alice]
```

`neverJoined` fails with `Player 'Alice' joined 'game', which it must not join`, and its backend variant
with `'proxy' connected player 'Alice' to 'auth', which it must not join`. For native state that
a join does not show, add an [agent operation](../../../../extending/agent-operations/index.md).
