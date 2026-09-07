---
title: Overview
description: Install and retrieve the typed actions and observations that a player journey needs.
---

Retrieve behavior by its public capability type with `player.capability(Type.class)`. Capabilities are
player-scoped; keep them with that player and retrieve new instances after creating a replacement.

All public capability APIs extend `PlayerCapability`, and a provider creates each instance for one
player. This scope does not require a connected session: `Server`, for example, uses platform agents
to observe that player's identity and route independently of `Session`. General server or proxy
control uses the context's [process API](../../environments/actions/index.md).

## Choose the behavior

For the first journey, use Session to connect, Server to observe arrival, and Messages to trigger
commands and check replies. Movement, Inventory, and Interaction cover additional game actions.
Each capability is optional unless the test or another installed capability requires it.

| Capability | Use it to | Bundled requirement |
| --- | --- | --- |
| [Session](./session/index.md) | Connect, disconnect, rejoin, and observe kicks. | MCProtocol |
| [Server](./server/index.md) | Inspect agent-observed routes, presence, and identity. | Platform agents |
| [Messages](./messages/index.md) | Send chat and commands; await received text. | MCProtocol and Session |
| [Movement](./movement/index.md) | Send absolute position and view changes. | MCProtocol and Session |
| [Inventory](./inventory/index.md) | Observe inventory or containers and perform clicks. | MCProtocol and Session |
| [Interaction](./interaction/index.md) | Use items and interact with blocks or entities. | MCProtocol and Session |

The `Server` capability does not depend on `Session`. Its observations come from the scenario's
platform agents, while packet-backed capabilities operate through the selected protocol backend.

## Install built-in capabilities

The umbrella `me.whereareiam.anvil` Gradle plugin includes all six built-ins. For a smaller JUnit setup,
this plugin block selects connection, messages, and server-observation behavior:

```kotlin
plugins {
	java
	id("me.whereareiam.anvil.junit") version "0.0.1"
	id("me.whereareiam.anvil.capability.session") version "0.0.1"
	id("me.whereareiam.anvil.capability.messages") version "0.0.1"
	id("me.whereareiam.anvil.capability.server") version "0.0.1"
	id("me.whereareiam.anvil.platform.paper") version "0.0.1"
}
```

Keep the repositories, protocol dependency, and `anvil` configuration from
[installation](../../../getting-started/installation/index.md). The remaining unit suffixes are
`movement`, `inventory`, and `interaction`. Keep unit versions aligned with Anvil.

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

Anvil selects a protocol backend before composing capabilities. An alternative backend needs its own
implementations of the capability APIs it supports; the bundled MCProtocol adapters are specific to
MCProtocol.

## Add an external capability

Add its consumer wiring artifact to `anvilCapabilities`:

```kotlin
dependencies {
	add("anvilCapabilities", "com.example:combat:1.0.0")
}
```

This coordinate is an example; use the artifact supplied by the capability author. See
[creating capabilities](../../../extending/capabilities/index.md) to publish one yourself.
