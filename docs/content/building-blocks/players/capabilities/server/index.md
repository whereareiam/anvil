---
title: Server observations
description: Inspect agent-observed player presence, identity, and aggregate routes.
---

`Server` exposes a player's agent-observed identity and aggregate route. Install
the server capability unit and the relevant platform units, or explicitly select the default capability unit with those
platform units. The capability uses platform agents and has no Session dependency.

## Wait for an observed route

After connecting `SimulatedPlayer alice`, import:

```java
import me.whereareiam.anvil.capability.server.Server;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertEquals;
```

For the first-test scenario's `server` process:

```java
var server = alice.capability(Server.class);
var identity = server.joined("server", Duration.ofSeconds(10));
assertEquals("Alice", identity.getObserved().getUsername());
assertEquals("server", identity.getRoute().getServer());
```

`joined` takes the scenario server name and waits until the aggregate route names that server. Anvil
follows the player's own connection: it asks the agent of the server or proxy the player connected to,
and a proxy's answer names the backend. Each agent is asked for the UUID the player was granted at login,
which a proxy forwards to its backends. Players sharing the username on another proxy or server, or a
username that differs only in letter case, do not affect the route, and a player that a proxy or login
plugin renamed is still found. Before the player is granted a profile, no agent is asked. It does not guarantee that the named backend's agent has independently observed the player.
When an assertion requires proof from that backend, use a backend-specific
[agent operation](../../../../extending/agent-operations/index.md) and await the native state you need.

## Read an observed identity

`identity()` returns the current snapshot immediately. It holds three profiles, each a username and a
UUID, from the client's request to the server the player is on:

| Accessor | Meaning |
| --- | --- |
| `getRequested()` | Profile the client asked for in its login request. |
| `getGranted()` | Profile the joined server or proxy granted in its login success; absent before the player logs in and after it disconnects. |
| `getObserved()` | Profile the backend reports, or the proxy's while no backend agent reports the player; absent when no agent observes the player. |
| `getRoute().getProxy()` | Scenario name of the proxy the player is connected through, when present. |
| `getRoute().getServer()` | Backend name reported by a server or proxy agent, when present. |

A proxy or login plugin may grant a profile that differs from the requested one, for example by renaming
`Steve` to `Steve_Premium` or by replacing an offline UUID with a premium one, and the backend reports what
the proxy forwarded. Assert the granted or observed profile when testing that plugin, and inspect the
requested profile separately. Use the route object for proxy and backend names.

## Expect that the player stays

`stayed(server, duration)` watches the observed route for a duration. It fails as soon as the player
is observed on another server or on none, naming where, and returns normally otherwise:

```java
server.stayed("auth", Duration.ofSeconds(3));
```

Use it for behavior that must not move the player, such as a refused command.

## Observe a route change or reconnect

After triggering your application's transfer to `lobby`, call `server.joined("lobby")`. After a kick
and rejoin, wait for a new session and the expected agent-observed route before reading identity again.
A matching route does not establish that plugin data, permissions, or authentication state
survived. Add the corresponding application assertion.

For topology setup, follow [proxy forwarding](../../../environments/platforms/proxies/forwarding/index.md).
For more detailed native server state, add an [agent operation](../../../../extending/agent-operations/index.md).
