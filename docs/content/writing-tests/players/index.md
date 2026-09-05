---
title: Creating players
description: Choose connection targets, manage sessions, and release simulated players in a test.
---

# Creating players

Create players from the running `AnvilContext` supplied to your test. Each name is unique among
the players currently registered in that context:

```java
var alice = anvil.players().create("Alice");
Session session = alice.capability(Session.class);
session.connect();
session.connected();
```

Creation does not connect the client. Session is a separately installed capability; calling
`connect()` starts login, and `connected()` waits until it completes.

## Select a player's connection target

```java
var alice = anvil.players().create(PlayerOptions.builder()
        .name("Alice")
        .connectTo("proxy")
        .build());
```

Omit `connectTo` to use the scenario entrypoint. Omit `clientVersion` to use the supported native
version compatible with the reachable servers. An explicit version must also be compatible.
Different native server versions reachable through one target are rejected rather than silently
translated.

Players start disconnected. Obtain `Session` and call `connect()` to join. Player amounts are
dynamic, and closing the scenario releases all remaining players and process resources.


## Manage the lifecycle

Use `session.disconnect()` to leave the current session and `session.rejoin()` to create a fresh
session. Use the corresponding waits when asserting asynchronous connection changes.

`alice.destroy()` permanently releases the player and unregisters its name. A later player can
reuse that name. Closing the scenario releases all remaining players automatically. A destroyed
player object is not reusable.

Player amounts are dynamic; create only the players the behavior under test needs. Use
[capabilities](../capabilities/index.md) to perform actions and assert observations. For an online
account profile, see [authentication](../../running-environments/authentication/index.md).
