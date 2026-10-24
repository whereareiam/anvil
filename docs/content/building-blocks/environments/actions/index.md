---
title: Overview
description: Find running process handles, execute commands, call agents, and restart or kill servers or proxies.
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
System.out.println(lobby.files().read("server.properties"));
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
| `kill(name)` | Stopped process, ended without its shutdown sequence |

Every handle exposes `name()`, `address()`, `files()`, `state()`, and `console()`.

## Read and change a process's files

`files()` reaches the process's workspace wherever its workbench keeps it. Paths are relative to the workspace and
separated by `/`; a path that leaves the workspace is refused. Its methods throw `IOException`.

```java
import me.whereareiam.anvil.api.process.ProcessFiles;
```

```java
ProcessFiles files = lobby.files();
String properties = files.read("server.properties");
files.write("plugins/Example/config.yml", "enabled: false\n");
List<String> plugins = files.list("plugins");
files.delete("plugins/Example");
```

Restarts keep the files: every generation of a process shares one workspace. `location()` describes where the
workspace is for diagnostics, such as its path on the local machine.
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

Choose the operation your test needs to perform against an existing server or proxy:

- [Running console commands](./console/index.md) shows bounded log observations.
- [Dispatching commands](./dispatch/index.md) runs a command as the console and reports whether the platform accepted it.
- [Interacting through agents](./agents/index.md) explains typed capabilities and native work inside a server or proxy.
- [Watching players on a process](./players/index.md) waits until a player is on a server and reads what a server or proxy knows about it.
- [Restarting processes](./restarts/index.md) covers workspace preservation and explicit player reconnection.
- [Killing processes](./kills/index.md) ends a server or proxy as if it had crashed, without its shutdown.
- [Cleanup diagnostics](../../../help/troubleshooting/cleanup/index.md) explains lifecycle failures and retention.
