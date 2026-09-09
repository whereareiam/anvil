---
title: Topology and forwarding
description: Negotiate proxy-to-server identity forwarding and verify the backend a player actually reaches.
---

Declare the proxy's backend names and default server, then let Anvil configure their allocated
addresses and forwarding settings. The complete [proxy environment](../index.md) supplies a working
Velocity-to-Paper topology.

## Compatible forwarding modes

| Proxy | Paper backend | Spigot backend |
|---|---|---|
| Velocity | Modern or legacy | Legacy |
| BungeeCord | Legacy | Legacy |

Anvil chooses a common mode from the installed providers before launch. Proxies with overlapping
backend sets belong to a connected forwarding group; they need a compatible configuration for the
shared servers. Conflicting online-authentication settings in a modern forwarding group fail validation.

Forwarded backend game listeners use offline authentication. Set online mode on the entry proxy
when testing authenticated player connections. The engine manages modern forwarding secrets and
platform configuration; do not store per-run forwarding values in fixture files.

## Assert the route

In a JUnit method receiving `ScenarioContext anvil`, this fragment targets the example proxy and
checks the backend. The umbrella plugin supplies the required Session and Server capabilities;
a smaller installation must add both units.

```java
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;

var alice = anvil.players().create(PlayerOptions.builder()
		.name("RouteAlice")
		.connectTo("proxy")
		.build());
var session = alice.capability(Session.class);
session.connect();
session.connected();
alice.capability(Server.class).joined("lobby");
```

Run the containing class using `./gradlew anvilTest --tests 'your.package.YourTest'`.
The first wait establishes the client connection. The final wait matches the route reported by
platform agents. In a proxied topology, the proxy agent's connected-backend report can satisfy this
wait; it does not require an independent observation from the backend agent. Use a backend-specific
[agent channelOperation](../../../../../extending/agent-operations/index.md) if your test needs proof of
application behavior inside that backend.

Use the same check after a server switch or [process restart](../../../actions/restarts/index.md).
Keep every reachable backend on the same [native version](../../versions/index.md).

## Forwarding and network exposure

Forwarding transfers identity information; it does not by itself prevent direct connections to a
backend. Local loopback listeners remain reachable by other programs on the host.
For Docker's private backend publication and its current networking limits, see
[execution providers](../../../configuration/execution/index.md).
