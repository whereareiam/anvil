---
title: Overview
description: Find running process handles, execute commands, call agents, and restart servers or proxies.
---

Use `ScenarioContext.processes()` to inspect the current server and proxy processes in a running
scenario. It is available inside an injected JUnit context or the context returned by
`ScenarioEngine.start()`.

## Find a process

This fragment assumes `anvil` is an open `ScenarioContext` from the
[proxy environment](../platforms/proxies/index.md):

```java
var processes = anvil.processes();
var proxy = processes.proxy("proxy");
var lobby = processes.server("lobby");
System.out.println(proxy.address());
System.out.println(lobby.workDirectory());
```

Names are the ones declared in the scenario. Lookups return current handles; typed lookups also
check whether the named process has the expected role.

| Operation | Result |
|---|---|
| `get(name)` | Any declared server or proxy |
| `server(name)` / `proxy(name)` | A process of the specified role |
| `all()` | Immutable snapshot of processes in launch order |
| `servers()` / `proxies()` | Immutable snapshot filtered by role |
| `restart(name)` | Replacement process after readiness |

Every handle exposes `name()`, `address()`, `workDirectory()`, `state()`, and `console()`.
Use `capability(Type.class)` for installed process behavior, including
[capabilities backed by agents](./agents/index.md).
After a restart, request a fresh lookup or use the returned replacement handle.
An earlier snapshot continues to contain the earlier handles.

## Own the lifecycle through the scenario

Anvil prepares declared assets and dependencies before configuring platforms. It starts servers
before proxies, waits for process and agent readiness, and runs the setup hook before returning the
context. Closing the context releases players and stops processes in reverse order.

Independent preparation and process startup may run concurrently within the configured limits.
A ready process says nothing about whether your plugin action succeeded: establish that with the
appropriate player, server, or console assertion.

## Choose an action

Choose the channelOperation your test needs to perform against an existing server or proxy:

- [Running console commands](./console/index.md) shows bounded log observations.
- [Interacting through agents](./agents/index.md) explains typed capabilities and native work inside a server or proxy.
- [Restarting processes](./restarts/index.md) covers workspace preservation and explicit player reconnection.
- [Cleanup diagnostics](../../../help/troubleshooting/cleanup/index.md) explains lifecycle failures and retention.
