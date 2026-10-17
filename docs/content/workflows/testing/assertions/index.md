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
| Player joined a server or proxy | `PlayerWatch.joined(player)` on that process's `Players.watch()` |
| Player left a server or proxy | `PlayerWatch.left(player)` |
| Proxy connected the player to a backend | `PlayerWatch.joined(player, backend)` on the proxy's `Players.watch()` |
| Proxy moved the player away from a backend | `PlayerWatch.left(player, backend)` on the proxy's watch |
| Inventory reached a condition | `Inventory.matching(predicate)` |
| New process output contains text | `ConsoleWatch.printed(text, timeout)` |

Capability waits offer explicit duration overloads; their default maximum wait is ten seconds.
Immediate snapshots such as `Session.state()`, `Messages.history()`, `Players.profile(player)`,
`Players.backend(player)`, and `Inventory.snapshot()` do not wait. `Players.backend(player)` can lag a
moment behind a move, so wait for the move on the proxy's watch instead of reading it straight after an
action. Create a `PlayerWatch` before the action it observes; see [watching players on a process](../../../building-blocks/environments/actions/players/index.md).

## Wait for whichever outcome comes first

Some tests have more than one correct outcome, decided by a race the test cannot order, such as the same
player acting through two proxies at once. Waiting for one outcome would then time out whenever the other
one happens. `SimulatedPlayer.awaitFirst` waits for the first of several expectations and returns its index;
the capability's ordinary wait for that outcome then returns at once:

```java
Messages messages = player.capability(Messages.class);
Session session = player.capability(Session.class);
MessageWatch chat = messages.watch();
messages.command("confirm 123456");

int outcome = player.awaitFirst(Duration.ofSeconds(10),
        chat.expectReceived(text -> text.contains("Invalid code")),
        session.expectKicked());
if (outcome == 1) assertTrue(session.kicked().contains("Reconnect"));
```

| Expectation | Met when |
| --- | --- |
| `MessageWatch.expectReceived(matcher)` | a message received since the watch's previous wait matches |
| `DialogWatch.expectShown(matcher)` | a dialog shown since the watch's previous wait matches |
| `CookieWatch.expectRequested(key)` | the server asked for the cookie since the watch's previous wait |
| `Session.expectKicked()` | the server kicked the current session |
| `Session.expectDisconnected()` | the current session is disconnected for any reason |
| `PlayerWatch.expectJoined(player)` | the player joined that watch's process since the watch's previous wait |
| `PlayerWatch.expectJoined(player, backend)` | the proxy connected the player to that backend since the watch's previous wait |
| `Expectation.of(description, condition)` | a check of your own that never waits holds |

When several expectations are met by the time they are checked, the earliest argument wins. A timeout names
every expectation, joined by "or". Use it only for outcomes that are really both correct; one expected
result still belongs in an ordinary wait, which explains a failure better.

## Watch console output before acting

Inside a live test with `ScenarioContext anvil` and the first-test process named `server`, import
`java.time.Duration` and use:

```java
var console = anvil.processes().server("server").console();
var output = console.watch();
console.sendCommand("say watch-verification");
String line = output.printed("watch-verification", Duration.ofSeconds(10));
output.neverPrinted("Exception", Duration.ofSeconds(2));
```

Start the watch before the command. The returned line must have been printed after the watch started,
so an earlier identical message cannot satisfy the wait, and each later wait continues after it. A watch
belongs to one process console; watch the new console after a
[process restart](../../../building-blocks/environments/actions/restarts/index.md).

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

Check the nearest evidence first: connection state, received messages, the players the expected process reports, then the
relevant process console tail. A command error or missing permission needs setup corrected, not a
longer wait. Increase an explicit timeout only when the operation legitimately needs more time.
Use [troubleshooting](../../../help/troubleshooting/index.md) for startup, routing, and retained diagnostics.
