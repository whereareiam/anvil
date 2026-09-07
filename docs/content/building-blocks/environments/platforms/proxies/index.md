---
title: Overview
description: Build a proxy entrypoint with named backend servers and verify the player route.
---

A `MinecraftProxy` accepts client connections and routes them to the servers registered in its
scenario declaration. Install the proxy platform unit and the unit for each backend platform.

| Platform | Gradle plugin ID | Distribution selector |
|---|---|---|
| [Velocity](./velocity/index.md) | `me.whereareiam.anvil.platform.velocity` | PaperMC Fill version and build |
| [BungeeCord](./bungeecord/index.md) | `me.whereareiam.anvil.platform.bungeecord` | Jenkins job and build |

## Define a proxy and backend

In an [installed Anvil project](../../../../getting-started/installation/index.md), apply both
`me.whereareiam.anvil.platform.paper` and `me.whereareiam.anvil.platform.velocity` at your Anvil version.
Place this definition at `src/anvil/java/com/example/test/ProxyEnvironment.java`:

```java
package com.example.test;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;

public final class ProxyEnvironment implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		var lobby = MinecraftServer.builder()
				.name("lobby")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.build();
		var proxy = MinecraftProxy.builder()
				.name("proxy")
				.platform(Platforms.VELOCITY)
				.distribution(Distribution.remote("3.5.1", "615"))
				.server(lobby.getName())
				.defaultServer(lobby.getName())
				.build();
		return AnvilScenario.builder()
				.name("proxy-environment")
				.entrypoint(proxy.getName())
				.server(lobby)
				.proxy(proxy)
				.build();
	}
}
```

Select `ProxyEnvironment.class` in the `@AnvilTest` annotation of the
[first-test pattern](../../../../getting-started/first-test/index.mdx), then run that class with
`./gradlew anvilTest --tests 'your.package.YourTest'`. The proxy becomes ready after its lobby.
Create a player, connect it through Session, and wait for `Server.joined("lobby")` to verify the route.

Each `.server(name)` on the proxy must refer to a declared scenario server. Its `defaultServer` must
be one of those names. Add additional backends to both the proxy's server list and the scenario.
Anvil allocates their addresses; do not hard-code ports into your proxy settings.

## Configure behavior at the right layer

Use platform `.setting(...)` entries for ordinary proxy configuration. Anvil owns the listener,
backend registry, default route, online mode, and negotiated forwarding fields.
The default proxy heap is 512 MiB and offline authentication is the default.

Use [forwarding](./forwarding/index.md) for identity and backend routing rules,
[authentication](../../../players/authentication/index.md) for online entrypoints, and
[process restarts](../../actions/restarts/index.md) for reconnect tests.
