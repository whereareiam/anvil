---
title: Assertions and waits
description: Connect asynchronous actions to precise observations and avoid matching old output.
---

Make each asynchronous step establish the fact needed by the next step. Starting login, sending a
command, and requesting an interaction do not imply their server-side effects have completed.

## Choose the right observation

| Expected result | Wait |
| --- | --- |
| Native login completed | `Session.connected()` |
| Current session disconnected | `Session.disconnected()` |
| Server kicked the session with a reason | `Session.kicked()` |
| Player received text | `Messages.received(text)` |
| Agent-observed route names the expected backend | `Server.joined(serverName)` |
| Inventory reached a condition | `Inventory.matching(predicate)` |
| New process output contains text | `ProcessConsole.await(text, checkpoint, timeout)` |

Capability waits offer explicit duration overloads; their default maximum wait is ten seconds.
Immediate snapshots such as `Session.state()`, `Messages.history()`, `Server.identity()`, and
`Inventory.snapshot()` do not wait.

## Checkpoint console output before acting

Inside a live test with `ScenarioContext anvil` and the first-test process named `server`, import
`java.time.Duration` and use:

```java
var console = anvil.processes().server("server").console();
long checkpoint = console.checkpoint();
console.sendCommand("say checkpoint-verification");
String line = console.await("checkpoint-verification", checkpoint, Duration.ofSeconds(10));
```

Capture the checkpoint before the command. The returned line must have arrived after that checkpoint,
so an earlier identical message cannot satisfy the wait. Checkpoints belong to one process console;
obtain a new console and checkpoint after a [process restart](../../../building-blocks/environments/actions/restarts/index.md).

## Avoid old messages satisfying new steps

`Messages.received(text)` searches captured history. If you send the same command twice and wait for
the same text, the first reply can satisfy both waits. Use a unique request/response token, a state
change your application exposes, or a new player where a fresh history fits the journey.

Similarly, `Inventory.matching(...)` can return immediately when the current snapshot already matches.
Write a condition that distinguishes the intended result from the initial state.

## Assert application behavior

The [first test](../../../getting-started/first-test/index.mdx) establishes connection, observed presence,
and message delivery. A plugin test should go further: trigger its behavior and assert the response,
identity, route, or application state it promises. Reconnection alone does not demonstrate persistence.

Use JUnit assertions on the observed result. Avoid fixed sleeps as a substitute for observation: they
neither establish completion nor explain which result was missing.

## Diagnose a timeout

Check the nearest evidence first: connection state, received messages, observed route, then the
relevant process console tail. A command error or missing permission needs setup corrected, not a
longer wait. Increase an explicit timeout only when the channelOperation legitimately needs more time.
Use [troubleshooting](../../../help/troubleshooting/index.md) for startup, routing, and retained diagnostics.
