---
title: Session
description: Connect and reconnect a native player and wait for login, disconnection, or a kick.
---

`Session` controls the current network connection. Install the session unit or use the umbrella plugin;
the bundled implementation requires MCProtocol. All fragments below use a live test's
`ScenarioContext anvil` and import `me.whereareiam.anvil.capability.session.Session`.

## Connect and await login

```java
var alice = anvil.players().create("Alice");
var session = alice.capability(Session.class);
session.connect();
session.connected();
```

`connect()` starts login. `connected()` waits for it to complete. The overload without a duration uses
the player default timeout of ten seconds; add `import java.time.Duration;` to supply another bound:

```java
session.connected(Duration.ofSeconds(20));
```

Use [`Server.joined(...)`](../server/index.md) after login to await an agent-observed route to a named
backend. A proxy can supply that route; it is not necessarily an observation from the backend itself.

## Disconnect and rejoin

After Alice has connected, use the existing `session` to leave and start a fresh connection:

```java
session.disconnect();
session.disconnected();

session.rejoin();
session.connected();
```

Disconnecting keeps the player object registered. Rejoining retains that player and its capability
instances, uses its configured identity and target, and resets Session's connection and kick state.
It does not create a replacement player or prove that plugin data survived. Other capabilities can
retain observations; see [Messages history](../messages/index.md#match-the-intended-channeloperation).
After login, wait for the expected agent-observed route and assert the application state you need.
A proxy or backend may report an identity different from the configured one; see
[server observations](../server/index.md).

## Observe a kick

For the direct Paper scenario from the first test, after Alice joins:

```java
anvil.processes().server("server").console()
		.sendCommand("kick Alice anvil-reconnect-test");
String reason = session.kicked();
```

`kicked()` waits for the current session's kick and returns its plain-text reason. Assert the expected
reason using JUnit rather than treating any disconnect as the correct application behavior. A network
disconnect and a server kick are different observations.

To try a fresh session afterwards:

```java
session.rejoin();
session.connected();
```

## Read state without waiting

`session.state().connected()` and `session.state().kickReason()` read the current snapshot immediately.
They are useful after a wait or for diagnostics. Reading `connected()` on the snapshot directly after
starting login may still return `false`; use the capability's wait for an asynchronous assertion.

To permanently release the player and reuse its name, see
[releasing and replacing players](../../connections/index.md#release-and-replace-a-player).
