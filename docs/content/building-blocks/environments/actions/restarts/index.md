---
title: Restarting processes
description: Replace one process while preserving its workspace and verify an explicit player reconnection.
---

Restart a process when a test needs to exercise initialization again using files already written to
its workspace. `processes.restart(name)` replaces that one server or proxy and waits for readiness.
Other processes continue running.

## Verify a proxy restart

Use the [proxy environment](../../platforms/proxies/index.md), with `proxy` as the entrypoint and
`lobby` as its backend. Enable rapid reconnections in the
[Velocity](../../platforms/proxies/velocity/index.md) or
[BungeeCord](../../platforms/proxies/bungeecord/index.md) declaration when its throttle would delay this test.
The Session and Server capabilities must be installed.

Place this helper at `src/anvil/java/com/example/test/ProxyRestartChecks.java`. Call
`ProxyRestartChecks.verify(anvil)` from a test method in the same package:

```java
package com.example.test;

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
		var original = processes.proxy("proxy");
		var lobby = processes.server("lobby");
		var alice = anvil.players().create("RestartAlice");
		var session = alice.capability(Session.class);
		var server = alice.capability(Server.class);

		session.connect();
		session.connected(Duration.ofSeconds(30));
		server.joined("lobby");

		var replacement = processes.restart("proxy");
		assertNotSame(original, replacement);
		assertEquals(ProcessState.STOPPED, original.state());
		assertEquals(ProcessState.READY, replacement.state());
		assertEquals(original.address(), replacement.address());
		assertEquals(original.workDirectory(), replacement.workDirectory());
		assertSame(lobby, processes.server("lobby"));

		session.disconnected();
		session.rejoin();
		session.connected(Duration.ofSeconds(30));
		server.joined("lobby");
	}
}
```

Run the containing test with `./gradlew anvilTest --tests 'your.package.YourTest'`.
This checks replacement of the proxy, preservation of the lobby, and reconnection of the same
registered player to that lobby.

To test persistence, write application state before the restart and assert that state after rejoining.
A successful reconnection alone does not prove that plugin data survived.

## What remains after a restart

| Resource | Behavior |
|---|---|
| Workspace | Same directory and data already written to disk |
| Game listener | Same address and port |
| Other processes | Continue running |
| Installed assets | Remain in place; initial asset installation and cache restoration do not repeat |
| Platform configuration | Reapplied before launch |
| Agent connection | Reestablished with fresh credentials |
| Process capability instances | Retained for the logical process; agent-backed operations may be unavailable during replacement |
| Original process handle | Remains attached to the stopped process |
| Simulated player | Remains registered; its connection needs an explicit reconnect |

Use the returned handle or look up the process again for further console or state observations.
Restarting a backend can disconnect or reroute a player according to the proxy's behavior;
assert that outcome before continuing the journey.

## Handle restart failures

Let unexpected restart failures fail the test and inspect the affected process's console log.
A `ProcessException` exposes its name through `getProcessName()`.
A failed restart marks the scenario lifecycle unsuccessful even if your test catches the exception;
success-only caches are not finalized as a successful run.

For newly installed assets or cache restoration, start a new scenario.
For data shared across separate runs, declare a [workspace policy](../../workspaces/index.md).
