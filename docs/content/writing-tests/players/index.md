---
title: Creating players
description: Create simulated players, choose where they connect, and release them when done.
---

# Creating players

Create players from `ScenarioContext.players()`. Each player name must be unique among the players
currently registered in that context:

```java
import me.whereareiam.anvil.capability.session.Session;

var alice = anvil.players().create("Alice");
Session session = alice.capability(Session.class);
session.connect();
session.connected();
```

A created player starts disconnected. Call `Session.connect()` to begin the login sequence and wait
for `Session.connected()` before sending commands or reading server state.

## Choose a route

```java
import me.whereareiam.anvil.api.model.player.PlayerOptions;

var alice = anvil.players().create(PlayerOptions.builder()
        .name("Alice")
        .connectTo("proxy")
        .clientVersion("1.21.11")
        .build());
```

`connectTo` names a server or proxy declared by the scenario. When it is omitted, the player uses
the scenario entrypoint. When `clientVersion` is omitted, Anvil selects the newest compatible
verified client version for the reachable servers. An explicit version must still be compatible
with every server the route can reach.

For online authentication profiles, set `authentication(AuthenticationMode.ONLINE)` and
`authenticationProfile("profile-name")`. See [Authentication](../../running-environments/authentication/index.md)
for the account-store flow.

## Release players

Call `alice.destroy()` to permanently unregister one player. Destroyed players cannot be reused,
but the name can be registered again by creating a new player later. Closing the scenario destroys
any remaining players automatically.

Use [player capabilities](../capabilities/index.md) to drive behavior and [player routing](routing.md)
to pick the right target for each player.
