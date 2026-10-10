---
title: Process lifetime
description: Keep a server or proxy running across scenarios when starting it for every scenario costs more than it proves.
---

A process normally starts with its scenario and stops when the scenario finishes, so every scenario
begins from freshly prepared files. A process that only has to be there, such as the backend servers
behind the proxy plugin you test, can instead keep running for the engine and serve one scenario after
another.

## Keep a process for the engine

In a scenario definition, import:

```java
import me.whereareiam.anvil.api.type.ProcessLifetime;
```

Add this to the server or proxy builder before `.build()`:

```java
.lifetime(ProcessLifetime.ENGINE)
```

The first scenario that declares the process starts it. When that scenario finishes, the process keeps
running, and the next scenario of the same engine that declares the same process uses it. The engine
stops it when the engine closes. Processes without the setting keep `ProcessLifetime.SCENARIO`.

This example keeps two Paper servers and starts a fresh Velocity proxy for every scenario:

```java
MinecraftServer lobby = MinecraftServer.builder()
		.name("lobby")
		.platform(Platforms.PAPER)
		.distribution(Distribution.remote("1.21.11", "132"))
		.lifetime(ProcessLifetime.ENGINE)
		.build();
MinecraftServer survival = lobby.toBuilder().name("survival").build();

MinecraftProxy proxy = MinecraftProxy.builder()
		.name("proxy")
		.platform(Platforms.VELOCITY)
		.distribution(Distribution.remote("3.5.1", "615"))
		.server(lobby.getName())
		.server(survival.getName())
		.defaultServer(lobby.getName())
		.build();
```

A scenario uses a kept process like any other: `anvil.processes()` returns it, players connect to it and
observe it, and its capabilities work. A scenario may stop or restart it; whatever state the scenario
leaves it in must be ready again when the scenario finishes, or the process is not kept.

## Know what the same process means

Two declarations name the same running process when the process declarations are equal, including the
workspace plan, settings, JVM arguments and Java selection, and when the scenarios agree on the
execution provider, network policy, process timeouts and the forwarding mode negotiated for the
process. All kept processes of a scenario are kept and handed over together, so declare the same set
in the scenarios that should share them. A scenario with a different set gets processes of its own.

A proxy in front of a kept server adopts the forwarding secret that server already runs with.

## Keep what a scenario proves in mind

Only one scenario uses a kept process at a time. Scenarios that run at once each get their own
processes, which are kept as well, so parallel tests need as many as run together.

A kept process carries its files, its world and whatever its plugins remember from one scenario to
the next. Keep a process only when no scenario depends on its starting state, and keep the process
under test fresh. Its [workspace](../workspaces/index.md) is prepared once, when it starts.

When a scenario fails, its kept processes stop with it and their workspaces are retained under the
same policy as the scenario's own, in a run directory named after the kept processes, such as
`build/anvil/engine-lobby-survival`. The next scenario starts new ones.

A proxy can be kept only together with all its servers. The local execution provider supports kept
processes; Docker execution refuses them, because each scenario's containers live on a private network.
