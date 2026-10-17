---
title: Dispatching commands
description: Run a command as a server or proxy console through the platform's dispatcher and check that it was accepted.
---

Use the `Dispatch` capability when a test needs a server or proxy to run a command as its console and needs
to know whether the platform accepted it. `Dispatch` belongs to the process: it hands the command to the
platform's own command dispatcher through the process's platform agent, so it needs no simulated player.

## Set up the capability

Apply the platform unit for each process and the `me.whereareiam.anvil.capability.dispatch` unit, or select
the default capability unit, which includes it:

```kotlin
plugins {
    id("me.whereareiam.anvil") version "0.0.1"
    id("me.whereareiam.anvil.capability.dispatch") version "0.0.1"
}
```

The command runs through the process's platform agent, which each platform unit includes.

## Dispatch a command

This helper expects a running Paper or Spigot process named `lobby`. Put it in
`src/anvil/java/com/example/test/DispatchChecks.java` and call `DispatchChecks.announce(anvil)` from a test
receiving `ScenarioContext anvil`:

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.dispatch.Dispatch;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DispatchChecks {
	public static void announce(ScenarioContext anvil) {
		var lobby = anvil.processes().server("lobby");
		var output = lobby.console().watch();

		assertTrue(lobby.capability(Dispatch.class).execute("say hello-from-anvil"));
		output.printed("hello-from-anvil", Duration.ofSeconds(10));
	}
}
```

Run the containing test with `./gradlew anvilTest`. `execute` takes the command without a leading slash and
returns whether the platform accepted it. Acceptance does not return output or prove that the command's work
finished, so the helper also waits for the line the server printed, through a console watch started before
the command. Assert on a typed observation instead when the command changes state a player or another
capability can see.

## Choose how to send a command

| You need | Use |
|---|---|
| The command run as the console, and whether the platform accepted it | `process.capability(Dispatch.class).execute(command)` |
| A line typed into the process's input, and what it printed in response | The process's [console](../console/index.md): `console().sendCommand(command)` with a `console().watch()` |
| The command run by a player, with that player's permissions | The player's [Messages capability](../../../players/capabilities/messages/index.md): `messages.command(command)` |

`Dispatch` and `console().sendCommand(...)` both run the command as the console, with the console's
permissions. `sendCommand` writes the line to the process's standard input and reports nothing about it; the
process's console only shows what the process printed. `Dispatch` goes through the platform's dispatcher and
reports the platform's answer. Type into the console when only the printed response matters, dispatch when the
test must fail on a rejected command, and combine both, as above, when it needs the response as well.

`Messages.command(...)` sends the command from the player's client. The server checks that player's
permissions, the player receives the response, and the test learns the outcome only from what the player sees.
Use it when the behavior under test is what a player may run; use `Dispatch` for setup and administration
that only the console is allowed to do.

## When a dispatch fails

- `CapabilityUnavailableException` when retrieving `Dispatch`: neither the dispatch unit nor the default unit is
  applied, so no provider supplies it, or the scenario has already closed the process's capabilities.
- `execute` returns `false`: the platform did not accept the command, for example because it does not know it.
  Read the process's console tail for the platform's response and check the plugin that registers the command.
- The request fails after a restart: the capability instance stays the same, but its agent connection belongs to
  the replacement process. Dispatch again after the [restart](../restarts/index.md) has completed.

`Dispatch` is one of the process capabilities [backed by agents](../agents/index.md); that page shows how to
call your own native operations the same way.
