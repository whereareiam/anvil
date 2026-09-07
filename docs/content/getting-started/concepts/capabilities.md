---
title: Capabilities
description: Use typed player behaviors without coupling journeys to one protocol backend.
---

# Capabilities

Capabilities keep `SimulatedPlayer` small and let providers add behavior for compatible protocol
backends. Resolve a capability from the player and use `hasCapability` when a journey supports
optional behavior:

This fragment assumes an active test method with a `SimulatedPlayer alice` variable:

```java
import me.whereareiam.anvil.capability.messages.Messages;

if (alice.hasCapability(Messages.class)) {
	Messages messages = alice.capability(Messages.class);
	messages.command("status");
	messages.received("Ready");
}
```

The built-in capability APIs are:

| Capability | Typical use |
| --- | --- |
| `Session` | Connect, disconnect, rejoin, and await kicks or connection state |
| `Messages` | Send chat or commands and await captured messages |
| `Server` | Observe server, proxy, backend, and player identity data |
| `Movement` | Send position, rotation, and on-ground state |
| `Inventory` | Inspect containers, select held slots, and click slots |
| `Interaction` | Use held items and interact with blocks or entities |

The umbrella plugin includes these built-in units. A smaller installation applies individual
`me.whereareiam.anvil.capability.<name>` Gradle plugins. Capability providers must be available on
the scenario runtime classpath; asking for an unavailable capability raises
`CapabilityUnavailableException`.

See [Capabilities](../../writing-tests/capabilities/index.md) for dependency selection and
capability-specific journeys, or [Extending Anvil](../../extending/index.md) to implement a new
capability.
