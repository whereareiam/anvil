---
title: Assertions and waits
description: Wait for observable behavior and assert the resulting player, session, and route state.
---

# Assertions and waits

Assert behavior after an observable event, not after a fixed sleep. Anvil exposes bounded waits on
the capabilities that produce the observation.

```java
import java.time.Duration;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;

var alice = anvil.players().create("Alice");
Session session = alice.capability(Session.class);
session.connect();
session.connected();

Messages messages = alice.capability(Messages.class);
Server server = alice.capability(Server.class);
messages.command("register secret");
messages.received("Registered");
server.joined("lobby", Duration.ofSeconds(10));
```

`SimulatedPlayer.DEFAULT_TIMEOUT` is ten seconds. Use a shorter or longer timeout when the expected
behavior is faster or slower than that default.

## Common assertions

| Observation | Wait or snapshot |
|---|---|
| Login completed | `session.connected(Duration.ofSeconds(10))` |
| Session ended | `session.disconnected(Duration.ofSeconds(10))` |
| Server kicked the player | `session.kicked(Duration.ofSeconds(10))` |
| Message arrived | `messages.received("ready", Duration.ofSeconds(10))` |
| Route observed | `server.joined("lobby", Duration.ofSeconds(10))` |
| Current player state | `player.state()` |
| Current session state | `session.state()` |
| Current route or identity | `server.identity()` |

Immediate snapshots are useful when you need to inspect the current state without waiting. For
example, `player.state().destroyed()` tells you whether a player has already been released, and
`messages.history()` returns the captured chat history so far.

If a behavior can be optional, check `hasCapability(type)` before resolving it. Missing capabilities
fail with a diagnostic because the runtime should make the absence explicit.

See [player capabilities](../capabilities/index.md) for the available built-in behaviors and
[player routing](../players/routing.md) for the route that a player should observe.
