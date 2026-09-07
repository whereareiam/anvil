---
title: Players
description: Create and control context-owned native-protocol players during a journey.
---

# Players

`PlayerManager` creates disconnected `SimulatedPlayer` instances on demand. A player belongs to
the active `ScenarioContext`; the context destroys remaining players during cleanup.

This fragment assumes a `ScenarioContext anvil` parameter in the active test method:

```java
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.player.SimulatedPlayer;

SimulatedPlayer alice = anvil.players().create("Alice");
SimulatedPlayer bob = anvil.players().create(PlayerOptions.builder()
		.name("Bob")
		.connectTo("lobby")
		.clientVersion("1.21.11")
		.build());
```

Names must be unique within the context. A player has no network connection until its `Session`
capability calls `connect()` or `rejoin()`. Destroy a player early with `destroy()` when a journey
no longer needs it; repeated destruction is safe.

Use `PlayerIdentity` from the `Server` capability to inspect the username, UUID, proxy route, and
backend observed by the environment. The client identity and server-observed identity can differ
when authentication or forwarding changes the profile.
