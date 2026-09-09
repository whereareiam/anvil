---
title: Interacting through agents
description: Use process capabilities backed by native server or proxy agents without creating a player.
---

Use an agent-backed process capability when a test needs to read or change state through the
server or proxy's native API. Retrieve the capability directly from the running process. The
process owns the behavior; its platform agent supplies the native implementation. This does not
require a simulated player or a game session.

## Execute a native command

This complete helper expects a running Paper or Spigot process named `lobby`. Apply its platform
unit and the `me.whereareiam.anvil.capability.console` unit, or use the umbrella Anvil plugin with
the platform unit. Put the helper in `src/anvil/java/com/example/test/AgentChecks.java` and call
`AgentChecks.announce(anvil)` from a test receiving `ScenarioContext anvil`:

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.console.Console;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AgentChecks {
	public static void announce(ScenarioContext anvil) {
		var lobby = anvil.processes().server("lobby");
		Console console = lobby.capability(Console.class);
		assertTrue(console.execute("say hello-from-anvil"));
	}
}
```

Run the containing test with `./gradlew anvilTest`. The boolean result reports whether the platform
accepted the command. It does not return console output or prove that the requested application
work finished. Use a typed observation or [captured process output](../console/index.md) to establish
that outcome.

The built-in `Console` requires an enabled platform agent. Capability retrieval fails with
`CapabilityUnavailableException` when the installed providers do not supply the requested type.
Use `process.hasCapability(type)` only when absence is an intentional supported case; it reports
whether the capability is installed, not whether its next request will succeed.

## Choose the owner and interaction

| Task | Entry point |
|---|---|
| Dispatch a command through the native platform API | The process's `Console` capability |
| Send console input or inspect captured process output | The process's [console](../console/index.md) |
| Read or change plugin state with a typed result | An installed process capability calling a native channelOperation |
| Observe one player's identity or route through agents | The player's [Server capability](../../../players/capabilities/server/index.md) |
| Restart one process | [Restarting processes](../restarts/index.md) |

A capability's owner and its implementation mechanism are separate choices. `Server` belongs to a
player because its observations concern that player. `Console` belongs to the process because the
command concerns that server or proxy. A custom process capability can use another implementation
without exposing an agent in its public API.

## Call an external capability

The [Echo example](../../../../extending/capabilities/contracts/index.md) supplies a process capability
that calls an installed handler. Add its host adapter to `anvilCapabilities`, and install its
[handler JAR](../../../../extending/agent-operations/installation/index.md) in `lobby`.
Inside a test with the running `ScenarioContext anvil`, use:

```java
import com.example.echo.Echo;

import static org.junit.jupiter.api.Assertions.assertEquals;

var lobby = anvil.processes().server("lobby");
assertEquals("echo:hello", lobby.capability(Echo.class).send("hello"));
```

Success establishes that the handler in `lobby` returned `echo:hello`. Replace Echo with an
channelOperation that returns the application state your test needs to assert. The result contains
serializable values, not the platform's live objects.

Process capability instances remain stable across a JVM restart. Retrieve the current process
handle for state and console observations; the replacement handle exposes the same capability
instances. Agent-backed requests can fail while the replacement connection is unavailable;
retry only after the process has started successfully.

Closing the scenario closes process capabilities after players and before their agent connections.
Subsequent capability lookup fails and `hasCapability(...)` returns `false`. Do not use retained
capability instances after that cleanup.

## Add native behavior

A host capability provider calls a typed channelOperation through its borrowed `RequestChannel`.
Anvil connects that channel to the process's agent client. The matching `AgentOperationProvider`
installs the handler inside the managed JVM. These are separate
extension points: adding the host dependency does not install the native handler.

Follow [Agent providers](../../../../extending/capabilities/agent-adapters/index.md) for host wiring
and [Contracts and handlers](../../../../extending/agent-operations/contracts/index.md) for native
implementation. Install handlers as workspace assets under `plugins/anvil-agent-extensions`.

Handlers access native services through `PlatformAgent.requireService(...)`. The bundled Bukkit
agent exposes `org.bukkit.Server`; Velocity and BungeeCord agents expose their proxy APIs.
Use `platform.call(...)` and follow the native API's threading rules.

If a request fails, check agent readiness, handler installation and service descriptors, matching
channelOperation contracts, and the target process's `anvil-console.log`.
