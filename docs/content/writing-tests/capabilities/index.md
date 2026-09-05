---
title: Player capabilities
description: Select and use portable player APIs backed by protocol adapters or platform agents.
---

# Player capabilities

A `SimulatedPlayer` exposes only capabilities installed in the runtime. Obtain a capability by its
public API type:

```java
var alice = anvil.players().create("Alice");
Session session = alice.capability(Session.class);
session.connect();
session.connected();
```

The API describes an action or observation. Its adapter decides how the selected backend or platform
performs it. Public capability APIs do not expose MCProtocolLib, platform SDK, or agent transport types.

## Built-in capabilities

| Capability | What it provides | Bundled implementation |
|---|---|---|
| `Session` | Connect, disconnect, rejoin, state, kick reasons | MCProtocolLib |
| `Messages` | Chat, commands, received-message history and waits | MCProtocolLib; requires Session |
| `Movement` | Position, rotation, on-ground state | MCProtocolLib; requires Session |
| `Inventory` | Inventory/container snapshots, held slot, clicks | MCProtocolLib; requires Session |
| `Interaction` | Item use, block and entity interactions | MCProtocolLib; requires Session |
| `Server` | Observed username, UUID, proxy/backend route | Platform-agent observation; no Session dependency |

The umbrella Gradle plugin includes the aggregate `default` wiring. With a smaller setup, apply
individual `me.whereareiam.anvil.capability.<name>` units: `session`, `messages`, `movement`,
`inventory`, `interaction`, or `server`.

## Wait for observable behavior

```java
Messages messages = alice.capability(Messages.class);
messages.command("my-plugin status");
messages.received("ready", Duration.ofSeconds(10));

Server server = alice.capability(Server.class);
server.joined("lobby", Duration.ofSeconds(10));
```

Use the capability's bounded waits when asserting asynchronous behavior. Fixed sleeps do not prove
that an expected packet, message, or route was observed. Missing capabilities fail with a diagnostic;
check `hasCapability(type)` when absence is an intentional part of a scenario.

Destroy a player with `alice.destroy()` when it is no longer needed. The scenario closes remaining
players on shutdown. Registered adapter cleanup runs in reverse order and continues after failures.

## External and alternative implementations

Add a capability wiring artifact to `anvilCapabilities`:

```kotlin
dependencies {
    add("anvilCapabilities", "com.example:combat:1.0.0")
}
```

A different protocol backend can implement the same `Session` or `Movement` API. Our MCProtocol
adapters do not translate automatically to another library. The engine first selects the backend,
then filters capability providers by their declared protocol IDs and validates actual dependencies.
Only one selected provider may contribute a particular capability type.

See [writing a capability](../../extending/capabilities/index.md),
[protocol backends](../../extending/protocol-providers/index.md), and
[agent operations](../../extending/agent-operations/index.md).
