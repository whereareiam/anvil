---
title: Interacting through agents
description: Run typed native operations inside a named server or proxy through its platform agent.
---

Use a platform agent when a test needs to read or change state through the server or proxy's native
API. The agent runs inside the managed process. An operation can inspect world or plugin state,
query proxy configuration, or perform other work that does not involve an online player.

## Choose the interaction you need

| Task | Entry point |
|---|---|
| Execute a console command or inspect process output | [Running console commands](../console/index.md) |
| Read or change native server/proxy state with a typed result | An installed agent operation exposed through a host capability |
| Restart one process | [Restarting processes](../restarts/index.md) |

Console commands use the process console directly. Native operations need a handler installed in the
process and a matching operation contract on the host. The handler calls the native API and returns
serializable result values; the test does not receive the platform's live objects.

## Understand the current host entry point

`ScenarioContext` and `RunningProcess` do not expose a direct `agents()` accessor. The current
extension API exposes `AgentDirectory` to `PlayerCapabilityContext`, which a host capability provider
receives when Anvil creates a player. Tests call the installed capability, and its adapter addresses
the required server or proxy by scenario process name.

This means the host-facing capability is player-scoped even when its operation concerns the process
itself. A simulated player must exist to obtain that capability. An operation that does not need a
game session can run while that player is disconnected; it does not need to connect or use `Session`.

## Call an installed operation

The [Echo example](../../../../extending/agent-operations/index.md) provides a small native-process
round trip. Before using this fragment:

- Install its [host capability adapter](../../../../extending/capabilities/agent-adapters/index.md) in
  `anvilCapabilities`.
- Install its [handler JAR](../../../../extending/agent-operations/installation/index.md) into a running
  server or proxy named `lobby` that supplies a platform agent.
- Supply `SimulatedPlayer player` created by that running scenario's player manager. The player can
  remain disconnected.

Inside a JUnit test with that supplied `player`, call:

```java
import com.example.echo.Echo;

import static org.junit.jupiter.api.Assertions.assertEquals;

String reply = player.capability(Echo.class).send("lobby", "hello");
assertEquals("echo:hello", reply);
```

Run the containing test with `./gradlew anvilTest`. Success establishes that the handler in `lobby`
received the request and returned `echo:hello`. It does not establish any application-specific state;
replace Echo with an operation that returns the state your test needs to assert.

The adapter resolves `AgentDirectory` with `context.requireService(AgentDirectory.class)`, where
`context` is its `PlayerCapabilityContext`. `agents.require("lobby")` selects that process's
`AgentClient`, and `request(operation, request)` invokes the shared typed operation. These clients
belong to the scenario; adapters borrow them and must not close them.

`AgentClient` also exposes built-in native console dispatch through `executeCommand(command)`.
Its boolean result reports whether the platform accepted the command; it does not return console
output or prove the application finished the requested work. Use an observation or a typed
operation result to establish that outcome.

## Add a native operation

Follow [Contracts and handlers](../../../../extending/agent-operations/contracts/index.md) to define the
request, result, and handler, then [install the operation JAR](../../../../extending/agent-operations/installation/index.md)
as a workspace asset under `plugins/anvil-agent-extensions`. A host capability dependency alone does
not install code inside the managed JVM.

Handlers access supported native services through `PlatformAgent.requireService(...)`. The bundled
Bukkit agent exposes `org.bukkit.Server`; Velocity and BungeeCord agents expose their proxy APIs.
Use `platform.call(...)` for the agent's scheduling rules. Bukkit schedules work onto its main
thread; the default implementation runs on the request thread. Handlers must also obey the threading
rules of the specific native API they call.

If a request fails, check the named process, agent readiness, installed handler and service descriptor,
and that process's `anvil-console.log`. Keep the host and handler operation contracts aligned. For the
complete host wiring, continue with [Agent adapters](../../../../extending/capabilities/agent-adapters/index.md).
