---
title: Processes
description: Understand the servers and proxies managed by an active scenario.
---

# Processes

Anvil manages each declared server or proxy as a named process. The process owns its listener
address, workspace, console, agent connection, and lifecycle state. Servers and proxies use the
same process contract; `MinecraftServer` and `MinecraftProxy` add role-specific declaration data.

Inside a running `ScenarioContext`, use `ScenarioProcesses` to inspect them. This fragment assumes
the test method has a `ScenarioContext anvil` parameter:

```java
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.whereareiam.anvil.api.type.ProcessState;

var server = anvil.processes().server("lobby");
assertEquals(ProcessState.READY, server.state());

long checkpoint = server.console().checkpoint();
server.console().sendCommand("say scenario ready");
server.console().await("scenario ready", checkpoint, Duration.ofSeconds(10));
```

`all()`, `servers()`, and `proxies()` return immutable snapshots. `get`, `server`, and `proxy`
resolve the current process generation by name. A restart returns a replacement process handle;
players remain registered but clients disconnected by the restart must explicitly reconnect.

Anvil starts servers before proxies and stops processes in reverse order. A failed startup or
restart marks the scenario unsuccessful even if the test catches the thrown exception. See
[Processes](../../running-environments/processes/index.md) for restart and console workflows.
