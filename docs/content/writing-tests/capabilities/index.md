---
title: Capabilities
description: Use typed player behaviors without coupling journeys to one protocol backend.
---

# Capabilities

A `SimulatedPlayer` exposes only the capabilities installed in the runtime. Resolve one by its
public API type:

```java
import me.whereareiam.anvil.capability.messages.Messages;

var alice = anvil.players().create("Alice");
Messages messages = alice.capability(Messages.class);
messages.command("my-plugin status");
messages.received("ready");
```

Capabilities describe behavior. Their adapters decide how the selected backend or platform fulfills
that behavior, so the public API stays free of backend-specific transport types.

## Built-in capabilities

| Capability | What it provides | Bundled implementation |
|---|---|---|
| `Session` | Connect, disconnect, rejoin, current session state, kick reasons | MCProtocolLib |
| `Messages` | Chat, commands, received-message history and waits | MCProtocolLib; requires `Session` |
| `Movement` | Position, rotation, and on-ground state | MCProtocolLib; requires `Session` |
| `Inventory` | Inventory and container snapshots, held slot, clicks | MCProtocolLib; requires `Session` |
| `Interaction` | Item use, block interaction, and entity interaction | MCProtocolLib; requires `Session` |
| `Server` | Observed username, UUID, and proxy/backend route | Platform-agent observation; no `Session` dependency |

Use `hasCapability(type)` when a behavior may be absent. `capability(type)` throws a diagnostic
when the requested behavior is not installed.

## Wait for behavior

```java
import java.time.Duration;

import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;

var alice = anvil.players().create("Alice");
Messages messages = alice.capability(Messages.class);
Server server = alice.capability(Server.class);
messages.command("my-plugin status");
messages.received("ready", Duration.ofSeconds(10));
server.joined("lobby", Duration.ofSeconds(10));
```

Use each capability's bounded waits when asserting asynchronous behavior. Fixed sleeps do not prove
that an expected packet, message, or route was observed. Immediate snapshots such as
`messages.history()`, `server.identity()`, and `player.state()` are useful when you need the current
state without waiting.

Destroy a player with `alice.destroy()` when it is no longer needed. The scenario closes remaining
players on shutdown. Registered adapter cleanup runs in reverse order and continues after failures.

## External implementations

Add a capability wiring artifact to `anvilCapabilities`:

```kotlin
dependencies {
    add("anvilCapabilities", "com.example:combat:1.0.0")
}
```

An alternative backend can implement the same `Session` or `Movement` API. Capability providers
declare the protocol IDs they support and the dependencies they actually need; Anvil selects from
those declared dependencies instead of guessing across the classpath.

See [writing a capability](../../extending/capabilities/index.md),
[protocol backends](../../extending/protocol-providers/index.md), and
[agent operations](../../extending/agent-operations/index.md).
