---
title: Running console commands
description: Send process commands and wait for fresh output using a console checkpoint.
---

Use a process console when the behavior under test is exposed through a console command or log line.
Take a checkpoint before the operation so an earlier matching line cannot satisfy the wait.

## Send a command and observe its output

This helper expects an open scenario with a Paper or Spigot server named `server`.
Place it at `src/anvil/java/com/example/test/ConsoleChecks.java` and call
`ConsoleChecks.verify(anvil)` from a test in the same package receiving `ScenarioContext anvil`:

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;

import java.time.Duration;

public final class ConsoleChecks {
	public static void verify(ScenarioContext anvil) {
		var console = anvil.processes().server("server").console();
		long before = console.checkpoint();
		console.sendCommand("say ANVIL_CONSOLE_CHECK");
		String output = console.await("ANVIL_CONSOLE_CHECK", before, Duration.ofSeconds(10));
		System.out.println(output);
	}
}
```

Run the containing test with `./gradlew anvilTest --tests 'your.package.YourTest'`.
Success means a fresh captured line contained the marker after the command. It does not prove a
player received that message; use the [Messages capability](../../../players/capabilities/messages/index.md)
when receipt by a player is the behavior under test.

`sendCommand` takes console syntax without a leading slash. For a plugin command, use a marker or
response that identifies that command's completion, rather than relying on unrelated periodic output.

## Read a bounded tail

`console.tail(50)` returns up to fifty of the most recently captured lines.
For full retained process output, inspect `anvil-console.log` in `workDirectory()` while the workspace
exists. Avoid printing unlimited logs into routine test output.

A checkpoint belongs to one process generation. After restarting a process, use the replacement
handle's console and take a new checkpoint. If a wait expires, inspect the tail for command syntax,
plugin initialization failures, and the actual response text before increasing the timeout.
See [startup troubleshooting](../../../../help/troubleshooting/startup/index.md).
