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

NeoForge has no identity forwarding, so a NeoForge server cannot be a backend of either proxy.

Anvil chooses a common mode from the installed providers before launch. Proxies with overlapping
backend sets belong to a connected forwarding group; they need a compatible configuration for the
shared servers. Conflicting online-authentication settings in a modern forwarding group fail validation.

Forwarded backend game listeners use offline authentication. Set online mode on the entry proxy
when testing authenticated player connections. The engine manages modern forwarding secrets and
platform configuration; do not store per-run forwarding values in fixture files.

## Assert the route

In a JUnit method receiving `ScenarioContext anvil`, this fragment targets the example proxy and
checks the backend. The default capability unit supplies the required Session and Players capabilities;
a smaller installation must add both units.

```java
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

Players lobby = anvil.processes().server("lobby").capability(Players.class);
Players proxy = anvil.processes().proxy("proxy").capability(Players.class);
PlayerWatch arrivals = lobby.watch();
PlayerWatch moves = proxy.watch();

var alice = anvil.players().create(PlayerOptions.builder()
		.name("RouteAlice")
		.connection(PlayerConnection.to("proxy"))
		.build());
var session = alice.capability(Session.class);
session.connect();
session.connected();

arrivals.joined(alice);
moves.joined(alice, "lobby");
assertEquals(Optional.of("lobby"), proxy.backend(alice));
assertEquals(alice.grantedProfile(), lobby.profile(alice).orElseThrow());
```

Run the containing class using `./gradlew anvilTest --tests 'your.package.YourTest'`.
The first wait establishes the client connection. `arrivals.joined(alice)` waits until the lobby's
own agent reports that Alice joined it, and `moves.joined(alice, "lobby")` until the proxy reports
connecting her to `lobby`. Only after that wait does `proxy.backend(alice)` reliably name `lobby`: it is
a snapshot of the proxy's reports, which can arrive a moment after the backend's. The last assertion compares the profile the lobby received through forwarding with
the one the proxy granted the client; a broken forwarding setup shows up there as another username or
UUID. Use a backend-specific [agent operation](../../../../../extending/agent-operations/index.md) if
your test needs proof of application behavior inside that backend.

Use the same check after a server switch or [process restart](../../../actions/restarts/index.md).
Keep every reachable backend on the same [native version](../../versions/index.md).

## Forwarding and network exposure

Forwarding transfers identity information; it does not by itself prevent direct connections to a
backend. Local loopback listeners remain reachable by other programs on the host.
For Docker's private backend publication and its current networking limits, see
[execution providers](../../../configuration/execution/index.md).
