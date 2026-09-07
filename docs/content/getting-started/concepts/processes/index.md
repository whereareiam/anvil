---
title: Processes
description: Work with the running server or proxy created from a scenario declaration.
---

A **process** is one running server or proxy created from your scenario. A `MinecraftServer`
declaration describes what to start; a `RunningServer` handle lets your test inspect and interact
with the server that was started. Proxies have the same distinction through `MinecraftProxy` and
`RunningProxy`.

For example, `PaperScenario` declares a server named `server`. Once the test receives its running
context, that name identifies a process with an allocated address, a workspace, a lifecycle state,
and a console. Alice connects to its game listener; your test can independently send commands through
its console.

## Observe a running server

This fragment uses the [running context](../context/index.md) introduced in the previous concept.
Assume that `ScenarioContext anvil` contains the declared process named `server`. Later, you can put
it inside the [first test](../../first-test/index.mdx). Add these imports to the calling code:

```java
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.process.type.RunningServer;
import java.time.Duration;
```

```java
RunningServer server = anvil.processes().server("server");
ProcessConsole console = server.console();
long before = console.checkpoint();
console.sendCommand("say process-ready-check");
String output = console.await("process-ready-check", before, Duration.ofSeconds(10));
```

When run inside the completed test, this waits for a fresh console line containing the marker. The
checkpoint excludes an earlier identical line. It proves the server produced that output; use Alice's
[Messages capability](../../../building-blocks/players/capabilities/messages/index.md) when the assertion
must prove that she received the message.

The context's typed lookup checks the role as well as the name: `server("server")` expects a server.
Use `get(name)` when the role does not matter, or `proxy(name)` for a proxy. The handle's `address()`
and `workDirectory()` help you inspect the allocated endpoint and the process's files without
hard-coding ports or guessing generated paths.

## Understand a process replacement

Restarting is useful when testing whether a plugin can initialize again using data it wrote before
shutdown. `anvil.processes().restart("server")` stops that process and returns a replacement handle
after readiness. The new process uses the same workspace and listener address, while other scenario
processes remain running.

A handle represents one process generation. The original `server` handle remains attached to the
stopped generation; use the returned replacement or look up the name again for later observations.
Take a fresh console checkpoint on the replacement. A disconnected Alice remains registered with the
scenario and needs an explicit reconnect.

Keep process operations inside the running [context](../context/index.md). Scenario teardown owns
stopping the processes, and handles do not become reusable fixtures for another test.
See [process operations](../../../building-blocks/environments/actions/index.md) for lookups and console use,
or [restarts](../../../building-blocks/environments/actions/restarts/index.md) for a complete reconnect and
persistence-checking workflow.

For typed calls into a server or proxy's native APIs, see
[Interacting through agents](../../../building-blocks/environments/actions/agents/index.md). That guide explains
the agent-backed adapter path separately from ordinary console commands.

Next: [Players](../players/index.md) introduces the clients that connect to these processes and
perform a journey.
