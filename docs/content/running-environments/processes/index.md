---
title: Inspect and restart processes
description: Find running servers and proxies and verify player reconnection after restarting one process.
---

# Inspect and restart processes

Use a process restart when your test needs to exercise plugin initialization again while preserving
the process workspace—for example, checking that a plugin can read state written before shutdown.
You can restart a proxy while its backends continue running, or restart one backend without replacing
the proxy.

This guide assumes Anvil is configured in your project and you have a running `ScenarioContext` from
an `@AnvilTest` method or `AnvilEngine.start()`. See [Getting started](../../getting-started/index.md)
for a first test and [Proxies and forwarding](../proxies/index.md) for a proxy with a lobby backend.

## Find a running process

Names come from the process declarations in the scenario:

```java
var processes = anvil.processes();
var proxy = processes.proxy("proxy");
var lobby = processes.server("lobby");
```

Use the typed lookup when your test requires a server or proxy. Use `get(name)` when the role does
not matter, such as looking up the scenario entrypoint. Each handle exposes its name, listener
address, workspace directory, and current state.

| Operation                      | Use it to…                                              |
|--------------------------------|---------------------------------------------------------|
| `get(name)`                    | Find any declared server or proxy                       |
| `server(name)` / `proxy(name)` | Find a process and verify its role                      |
| `all()`                        | Inspect every process in launch order                   |
| `servers()` / `proxies()`      | Inspect processes of one role                           |
| `restart(name)`                | Stop and replace one process, waiting until it is ready |

The collection methods return immutable snapshots. Keep a snapshot to compare the environment
before and after an operation; request a new one when you need the current process handles.

## Verify reconnection after a proxy restart

The following helper can be called from your test with `ProxyRestartChecks.verify(anvil)`. It expects
a proxy named `proxy` as the scenario entrypoint and a backend named `lobby` as its default server.
Apply the platform units for both processes and install the Session and Server capabilities.

```java
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class ProxyRestartChecks {
    public static void verify(ScenarioContext anvil) {
        var processes = anvil.processes();
        var originalProxy = processes.proxy("proxy");
        var lobby = processes.server("lobby");
        var alice = anvil.players().create("RestartAlice");
        var session = alice.capability(Session.class);
        var server = alice.capability(Server.class);

        session.connect();
        session.connected(Duration.ofSeconds(30));
        server.joined("lobby");

        var replacement = processes.restart("proxy");

        assertNotSame(originalProxy, replacement);
        assertEquals(ProcessState.STOPPED, originalProxy.state());
        assertEquals(ProcessState.READY, replacement.state());
        assertEquals(originalProxy.address(), replacement.address());
        assertEquals(originalProxy.workDirectory(), replacement.workDirectory());
        assertSame(lobby, processes.server("lobby"));
        assertEquals(ProcessState.READY, lobby.state());

        session.disconnected();
        session.rejoin();
        session.connected(Duration.ofSeconds(30));
        server.joined("lobby");
    }
}
```

The test first establishes a real player connection. It then checks that the proxy was replaced,
that the lobby was preserved, and that the same player can reconnect and reach the lobby again.
If the proxy enforces a connection throttle, configure it in the scenario to permit the rapid
reconnection this test performs.

To turn this into a persistence test, perform the plugin action that writes state before `restart`,
then assert that state through the plugin after the player rejoins. A successful reconnection alone
does not prove that plugin data survived.

## Use the replacement handle

`restart(name)` returns the new process only after readiness and platform-agent reconnection.
The previous handle continues to describe the stopped process. Use the returned handle, or look up
the name again through the same `ScenarioProcesses` object, for subsequent inspection.

The simulated player remains registered, but restarting its proxy disconnects its client. Reconnect
explicitly, as shown above. Restarting a backend may disconnect a player or move it elsewhere,
depending on the proxy's behavior; assert that behavior before performing the next action.

## Know what restart preserves

| Part of the environment | After restarting one process |
|---|---|
| Process workspace | Same directory, including plugin data already written to disk |
| Listener address | Same address and port |
| Other processes | Continue running |
| Installed assets and cached files | Remain in place; installation and cache restoration are not repeated |
| Platform-owned configuration | Reapplied before launch |
| Platform agent | Reconnected with fresh credentials; borrowed agent handles follow the replacement |
| Previously obtained process handle | Remains attached to the stopped process |

Use a new scenario run when you need assets installed again or a newly prepared workspace. Use
workspace cache declarations when data must survive between separate runs; restart itself does
not create a cache snapshot. See [Workspace assets and caches](../../writing-tests/workspaces/index.md).

## Diagnose a failed restart

Let an unexpected restart exception fail the test. Anvil keeps failure diagnostics according to
the configured workspace-retention policy. Read the affected process's `anvil-console.log`, starting
with the first startup failure. A `ProcessException` exposes the affected process name through
`getProcessName()`.

If your test deliberately catches a restart failure, the scenario still remains unsuccessful:
success caches are not saved when it closes. A later successful assertion does not clear that state.
See [Troubleshooting](../troubleshooting/index.md) for failure categories and the next checks to make.

## Updating process imports

Process APIs are under `me.whereareiam.anvil.api.process`; import `RunningServer` and `RunningProxy`
from its `type` subpackage. `ScenarioContext` and `ScenarioEngine` are under
`me.whereareiam.anvil.api.scenario`. Consumers using the former `api.runtime` packages must update
imports and recompile against the matching Anvil artifacts.
