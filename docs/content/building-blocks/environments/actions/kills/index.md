---
title: Killing processes
description: End a server or proxy without its shutdown sequence to test crash recovery, then start it again.
---

Kill a process when a test needs to know what a plugin does when a server or proxy dies: whether it
recovers its state, expires locks or heartbeats, or lets players reconnect. `processes.kill(name)`
terminates that one process at once, so the platform runs no stop command, shutdown hooks, or plugin
disable logic. `processes.stop(name)` is the clean counterpart.

## Kill a proxy under a connected player

Use the [proxy environment](../../platforms/proxies/index.md), with `proxy` as the entrypoint and
`lobby` as its backend. Enable rapid reconnections in the proxy declaration when its throttle would
delay the rejoin. The Session and Server capabilities must be installed.

Place this helper at `src/anvil/java/com/example/test/ProxyCrashChecks.java`. Call
`ProxyCrashChecks.verify(anvil)` from a test method in the same package:

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ProxyCrashChecks {
	public static void verify(ScenarioContext anvil) {
		var processes = anvil.processes();
		var alice = anvil.players().create("CrashAlice");
		var session = alice.capability(Session.class);
		var server = alice.capability(Server.class);
		session.connect();
		session.connected(Duration.ofSeconds(30));
		server.joined("lobby");

		var killed = processes.proxy("proxy");
		processes.kill("proxy");

		session.disconnected(Duration.ofSeconds(30));
		assertEquals(DisconnectCause.CONNECTION_LOST, session.state().disconnectCause());
		assertEquals(ProcessState.STOPPED, killed.state());
		assertEquals(ProcessState.READY, processes.server("lobby").state());

		// Assert what your plugin left behind here, for example a lock it must release after a crash.

		processes.start("proxy");
		session.rejoin();
		session.connected(Duration.ofSeconds(30));
		server.joined("lobby");
	}
}
```

Run the containing test with `./gradlew anvilTest --tests 'your.package.YourTest'`.

## What a kill does

| Resource | Behavior |
|---|---|
| Process | Terminated at once: a local process tree with `SIGKILL` through `destroyForcibly`, a Docker container with `docker kill` |
| Shutdown sequence | Not run: no stop command, shutdown hooks, or plugin disable logic |
| Process handle | Ends `STOPPED`, like after a stop |
| Agent connection and launch attachments | Released, like on a stop |
| Workspace and listener address | Retained; `start(name)` creates a new generation on the same address |
| Other processes | Keep running, including the processes that depend on the killed one |
| Connected players | Lose their connection with `DisconnectCause.CONNECTION_LOST` and must reconnect explicitly |

Killing a process that never started or already stopped has no effect.

## A kill is not a crash

A deliberate kill does not mark the scenario unsuccessful, so success-only caches are finalized and the
run is reported as passed when your assertions pass. Its console history and the workspace's
`anvil-console.log` end with an Anvil line naming it:

```text
[Anvil] Process 'proxy' was killed on request without a clean shutdown
```

A process that exits on its own still fails the scenario, also when a test kills it afterwards: the kill
then only finishes the stop of a generation that had already crashed, and the console carries no kill line.

## Limits

- A kill cannot be undone: data the platform had not written to disk is lost, exactly as in a real crash.
  Assert that loss deliberately rather than relying on it.
- A killed proxy sends its backends no disconnect messages; they only see their connections to it closed
  by the operating system, and handle that as their platform does.
- Docker execution kills the whole container; a local process is killed together with the processes it
  started.
- `ProcessException` reports a process that does not exit within the configured shutdown timeout after
  it is killed, and that failure marks the scenario unsuccessful.
