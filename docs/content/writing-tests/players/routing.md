---
title: Player routing
description: Choose the entrypoint, backend, and client version for each simulated player.
---

# Player routing

Use `PlayerOptions` when one player needs a specific route, client version, or authentication
profile. The route name must match a server or proxy declared by the scenario.

```java
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.api.model.player.PlayerOptions;

var alice = anvil.players().create(PlayerOptions.builder()
        .name("Alice")
        .connectTo("proxy")
        .clientVersion("1.21.11")
        .build());
```

An omitted `connectTo` value uses the scenario entrypoint. That entrypoint may be a server or a
proxy, depending on how the scenario is defined. When `clientVersion` is omitted, Anvil picks the
newest compatible verified client version for the reachable servers. An explicit version must still
match every server on that route.

Use `authentication(AuthenticationMode.ONLINE)` together with
`authenticationProfile("profile-name")` when the route should use an installed online profile.
Offline players remain the default.

The `Server` capability exposes the observed proxy and backend route after the player connects. If
you need to assert that a route changed after a restart, wait on `Server.joined(...)` rather than
checking a fixed delay.
