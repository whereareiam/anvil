---
title: Reading a server's mod channels
description: List the payload channels a NeoForge server registered and let simulated players join it as NeoForge clients.
---

Use the `Network` capability to read the payload channels a NeoForge server registered with NeoForge's network:
NeoForge's own and those of its mods, each with its version, phase, direction and whether a client may lack it. A
NeoForge client claims these channels while its configuration starts, and the server refuses one that lacks a channel
it requires or claims it at another version. A simulated player created with these channels joins a server whose mods
need their client counterparts, as a client with the same mods does.

## Set up the capability

Apply the NeoForge platform unit and the `me.whereareiam.anvil.capability.network` unit, or select the default
capability unit, which includes it:

```kotlin
plugins {
    id("me.whereareiam.anvil") version "0.0.1"
    id("me.whereareiam.anvil.capability.network") version "0.0.1"
}
```

The channels are read by the server's NeoForge agent, which the NeoForge platform unit includes. NeoForge keeps them to
itself, so the agent reads them through an accessor of its own. Only NeoForge servers have the capability.

## Join a modded server as a NeoForge client

This helper expects a running NeoForge 1.21.1 process named `server` whose mods need a client counterpart. Put it in
`src/anvil/java/com/example/test/ModdedJoin.java` and call `ModdedJoin.join(anvil)` from a test receiving
`ScenarioContext anvil`:

```java
package com.example.test;

import me.whereareiam.anvil.api.model.player.PlayerClient;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.network.Network;
import me.whereareiam.anvil.capability.session.Session;

public final class ModdedJoin {
	public static SimulatedPlayer join(ScenarioContext anvil) {
		var channels = anvil.processes().server("server").capability(Network.class).channels();
		SimulatedPlayer alice = anvil.players().create(PlayerOptions.builder()
				.name("Alice")
				.client(PlayerClient.neoforge(channels))
				.build());
		alice.capability(Session.class).connect();
		alice.capability(Session.class).connected();

		return alice;
	}
}
```

The player then answers NeoForge's configuration as a NeoForge client does (registry sync, configuration files,
registry data maps, extensible enums, feature flags) and joins. It runs no mod code: the payloads the mods send on their
channels arrive and are left alone. See [NeoForge clients](../../../players/connections/index.md#present-a-neoforge-client)
for what such a player can and cannot do.

## Check that a mod registered its channel

```java
assertTrue(anvil.processes().server("server").capability(Network.class).channels().stream()
		.anyMatch(channel -> channel.getId().equals("example:sync")));
```
