---
title: Platforms
description: Choose the server and proxy implementations that your test needs.
---

A **platform** is the implementation of a server or proxy in your environment. Paper and Spigot are
server platforms: they run worlds and server plugins. Velocity and BungeeCord are proxy platforms:
they accept player connections and route them to backend servers. Both roles belong to the same
scenario and share Anvil's process lifecycle.

You choose platforms according to the behavior being tested. A Paper plugin's command may need only
one Paper server. A login or routing plugin may need a Velocity proxy with a Paper server named
`lobby`, so Alice connects through the proxy before reaching the world.

## Describe the roles together

This fragment continues the [scenario declaration](../scenarios/index.md), replacing its single
server with a lobby and a proxy. Read it as declaration code for now. To run this variant later,
follow [installation](../../installation/index.md) and install both Paper and Velocity platform units.
Add these imports to the scenario definition:

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;
```

```java
MinecraftServer lobby = MinecraftServer.builder()
		.name("lobby")
		.platform(Platforms.PAPER)
		.distribution(Distribution.remote("1.21.11", "132"))
		.build();
MinecraftProxy proxy = MinecraftProxy.builder()
		.name("proxy")
		.platform(Platforms.VELOCITY)
		.distribution(Distribution.remote("3.5.1", "615"))
		.server(lobby.getName())
		.defaultServer(lobby.getName())
		.build();
```

In the scenario's return builder, register `.server(lobby)` and `.proxy(proxy)`, and replace the
entrypoint with `.entrypoint(proxy.getName())`. The resulting route is Alice → proxy → lobby.
When adapting the first test, also change its expected backend and console lookup from `server`
to `lobby`; the player uses the new proxy entrypoint automatically. These declarations do not start
either process; the engine starts the completed scenario.

`Platforms.PAPER` selects how Anvil prepares and runs the lobby. Its
[distribution](../distributions/index.md) separately selects which Paper executable to use.
The proxy's `.server(...)` connects a named backend to the route; it does not create another server.

## What the platform supplies

The installed platform unit supplies the provider that resolves the executable, writes the platform's
configuration, and recognizes startup readiness. It also supplies the matching agent that Anvil
installs for native observations. Your test uses the same process and player APIs across these roles;
you do not assign agent ports or copy agent credentials into the scenario.

Anvil configures the allocated listener addresses and negotiates compatible proxy forwarding. A
platform choice still needs compatible distributions and native player versions. Applying a Paper
unit, for example, does not make a Spigot-only or Velocity-specific declaration available.

For detailed setup, see [server and proxy platforms](../../../building-blocks/environments/platforms/index.md)
and the [complete proxy environment](../../../building-blocks/environments/platforms/proxies/index.md).

Next: [Distributions](../distributions/index.md) explains how to choose the exact executable for
the platform you selected.
