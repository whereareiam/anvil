---
title: Proxies and forwarding
description: Connect server processes through a proxy and negotiate compatible identity forwarding.
---

# Proxies and forwarding

Declare the servers and proxy in one [scenario](../../writing-tests/scenarios/index.md). A proxy
lists its reachable server names and a default server; those names also identify routes in player
observations.

## Add a proxy

This topology fragment assumes a Paper `lobby` server has already been declared:

```java
MinecraftProxy proxy = MinecraftProxy.builder()
        .name("proxy")
        .platform(Platforms.VELOCITY)
        .distribution(Distribution.remote("3.5.1", "615"))
        .server(lobby.getName())
        .defaultServer(lobby.getName())
        .build();

AnvilScenario scenario = AnvilScenario.builder()
        .name("proxy-route")
        .entrypoint(proxy.getName())
        .server(lobby)
        .proxy(proxy)
        .build();
```

Apply both platform unit plugins. The engine allocates listeners, starts servers before proxies,
and provides backend addresses to the proxy configuration. Providers declare their supported
forwarding modes; Anvil negotiates a common mode for the connected group before launch.

Paper accepts modern and legacy forwarding. Spigot accepts legacy forwarding. Velocity can send
either; BungeeCord uses legacy. Overlapping proxy routes share a compatible configuration, and
conflicting modern-proxy authentication settings fail preflight. Forwarded backend game listeners
use offline authentication; the entry proxy can authenticate players online.


## Verify where a player arrived

Create a player targeting the proxy, connect through its Session capability, then use the Server
capability to wait for the expected backend:

```java
var alice = anvil.players().create(PlayerOptions.builder()
        .name("Alice")
        .connectTo("proxy")
        .build());
alice.capability(Session.class).connect();
alice.capability(Session.class).connected();
alice.capability(Server.class).joined("lobby");
```

This fragment assumes the named proxy and lobby exist. The Server capability combines native
player identity with agent observations; it does not infer a backend solely from a client socket.

Use the same native Minecraft version on every backend reachable by a player. Anvil rejects
incompatible reachable server versions rather than adding protocol translation. See
[platforms and versions](../platforms/index.md) and [authentication](../authentication/index.md).

## Restarting a process during a test

```java
var restarted = anvil.restart("proxy");
alice.capability(Session.class).rejoin();
alice.capability(Session.class).connected();
alice.capability(Server.class).joined("lobby");
```

Restart preserves the process workspace and game address, including a fresh workspace's database.
Backends keep running when a proxy restarts. Anvil stops the old process, reapplies platform-owned
configuration, starts a replacement, waits for readiness, and reconnects its agent with fresh
credentials. Assets and caches are not reinstalled. Existing player capabilities borrow refreshed
agent handles; reconnect disconnected sessions explicitly. Use the returned process handle or resolve
it again through the context after restart. Old process handles represent the stopped generation.

A restart failure retains failure diagnostics and prevents success-cache saves even when the test
catches the exception. Final cleanup still attempts every resource.
