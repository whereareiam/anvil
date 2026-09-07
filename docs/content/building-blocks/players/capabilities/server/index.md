---
title: Server observations
description: Inspect agent-observed player presence, identity, and aggregate routes.
---

`Server` exposes a player's agent-observed identity and aggregate route. Install
the server capability unit and the relevant platform units, or use the umbrella plugin with those
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
assertEquals("Alice", identity.getObservedUsername());
assertEquals("server", identity.getRoute().getServer());
```

`joined` takes the scenario server name and waits until the aggregate route names that server. The
route can come from either a backend agent's presence observation or a proxy agent's connected-server
observation. It does not guarantee that the named backend's agent has independently observed the player.
When an assertion requires proof from that backend, use a backend-specific
[agent operation](../../../../extending/agent-operations/index.md) and await the native state you need.

## Read an observed identity

`identity()` returns the current snapshot immediately. The fields distinguish the client's configured
identity from what the platform observed:

| Accessor | Meaning |
| --- | --- |
| `getUsername()` | Configured player username. |
| `getClientUniqueId()` | Client identity used by the player. |
| `getObservedUsername()` | Username in the current aggregate agent observation; can be absent. |
| `getObservedUniqueId()` | Unique ID in the current aggregate agent observation; can be absent. |
| `getRoute().getProxy()` | Observed proxy name, when present. |
| `getRoute().getServer()` | Backend name reported by a server or proxy agent, when present. |

An authentication or forwarding plugin may change the observed identity. Assert the observed value
when testing that plugin, and inspect the client value separately. Use the route object for proxy and
backend names.

## Observe a route change or reconnect

After triggering your application's transfer to `lobby`, call `server.joined("lobby")`. After a kick
and rejoin, wait for a new session and the expected agent-observed route before reading identity again.
A matching route does not establish that plugin data, permissions, or authentication state
survived. Add the corresponding application assertion.

For topology setup, follow [proxy forwarding](../../../environments/platforms/proxies/forwarding/index.md).
For more detailed native server state, add an [agent operation](../../../../extending/agent-operations/index.md).
