---
title: Overview
description: Install and retrieve the typed actions and observations that a player journey needs.
---

Retrieve player behavior by its public capability type with `player.capability(Type.class)`. Keep
these instances with that player and retrieve new ones after creating a replacement.

The capabilities in this section extend `PlayerCapability`; a provider creates each instance for
one player. They cover what a Minecraft client can normally do or see: its connection, the chat it
receives, its inventory, the dialogs it is shown. What a server or proxy knows about a player, such as
whether the player is on it, the profile it sees, or the backend a proxy connected the player to,
belongs to that process's [Players](../../environments/actions/players/index.md) capability. General
server or proxy control uses the context's [process API](../../environments/actions/index.md) or a
capability owned by its [agent](../../environments/actions/agents/index.md), such as `Console`.

## Choose the behavior

For the first journey, use Session to connect, the server's
[Players](../../environments/actions/players/index.md) to observe arrival, and Messages to trigger commands
and check replies. Movement, Inventory, Interaction, Commands, and Dialogs cover additional game actions.
Each capability is optional unless the test or another installed capability requires it.

| Capability | Use it to | Bundled requirement |
| --- | --- | --- |
| [Session](./session/index.md) | Connect, disconnect, rejoin, and observe kicks. | MCProtocol |
| [Messages](./messages/index.md) | Send chat and commands; await received text. | MCProtocol and Session |
| [Movement](./movement/index.md) | Send absolute position and view changes. | MCProtocol and Session |
| [Inventory](./inventory/index.md) | Observe inventory or containers and perform clicks. | MCProtocol and Session |
| [Interaction](./interaction/index.md) | Use items and interact with blocks or entities. | MCProtocol and Session |
| [Commands](./commands/index.md) | Read the command tree and check commands as the client does. | MCProtocol and Session |
| [Dialogs](./dialogs/index.md) | Observe dialogs, fill their inputs, and press their buttons. | MCProtocol and Session, Minecraft 1.21.6+; Commands for buttons that run commands |

Packet-backed capabilities operate through the player's protocol library. The built-in capabilities
are installed on every MCProtocolLib release Anvil ships, from Minecraft 1.18.2 to 26.1.2,
except Dialogs, which needs a release with dialogs: 1.21.11 and 26.1.2.

## Install built-in capabilities

The `me.whereareiam.anvil.capability.default` Gradle plugin includes these player capabilities. For a smaller JUnit setup,
this plugin block selects connection and messages behavior, and the process-owned Players capability
that reports who is on each server:

```kotlin
plugins {
	java
	id("me.whereareiam.anvil.junit") version "0.0.1"
	id("me.whereareiam.anvil.capability.session") version "0.0.1"
	id("me.whereareiam.anvil.capability.messages") version "0.0.1"
	id("me.whereareiam.anvil.capability.players") version "0.0.1"
	id("me.whereareiam.anvil.platform.paper") version "0.0.1"
}
```

Keep the repositories, protocol dependency, and `anvil` configuration from
[installation](../../../getting-started/installation/index.md). The remaining unit suffixes are
`movement`, `inventory`, `interaction`, `commands`, and `dialogs`. Keep unit versions aligned with Anvil.

## Retrieve a capability

In a running test, import `me.whereareiam.anvil.capability.messages.Messages` and use a connected player:

```java
Messages messages = alice.capability(Messages.class);
messages.chat("hello-from-anvil");
```

Retrieval fails with `CapabilityUnavailableException` if the runtime does not supply that capability.
Use `hasCapability(type)` only when absence is an intentional supported branch of the test. If the
journey requires messages, let a missing `Messages` implementation fail rather than silently skipping
the assertion.

Anvil selects each player's protocol library before composing its capabilities. Another library needs
its own implementations of the capability APIs it supports; the bundled MCProtocol adapters are
specific to MCProtocolLib. When the player's worker cannot install a capability, for example because
the release lacks a class it needs, retrieval throws `CapabilityUnavailableException` with that reason
while the player's other capabilities keep working.

## Add an external capability

Add its consumer wiring artifact to `anvilImplementation`:

```kotlin
dependencies {
	add("anvilImplementation", "com.example:combat:1.0.0")
}
```

This coordinate is an example; use the artifact supplied by the capability author. See
[creating capabilities](../../../extending/capabilities/index.md) to publish one yourself.
