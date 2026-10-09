---
title: Running context
description: Understand the handle to one active scenario and the resources it owns.
---

A running context is your handle to one active scenario execution. The scenario declaration says
which processes and assets the environment needs; `ScenarioContext` gives your code access to the
processes and players that belong to the execution started from that declaration.

Use the context whenever a journey needs to create a player, find a named process, send a console
command, or restart part of the environment. It keeps those operations tied to the correct run,
including the endpoints Anvil assigned during startup.

## What the context provides

| Method | What you obtain |
| --- | --- |
| `definition()` | The declaration used for this running scenario. |
| `processes()` | Named process lookup, console access, and lifecycle operations. |
| `players()` | The factory and registry for this scenario's simulated players. |

The caller receives the context after the engine starts the environment and completes its setup
hook. During setup, the callback receives `ScenarioAccess`, a view of the same declaration,
process, and player services without a `close()` method. The [definition guide](../../../building-blocks/environments/definitions/index.md#prepare-live-state)
explains that preparation step.

A context is a runtime resource; it is not a global configuration object or a declaration you
can reuse to represent a later execution.

## Example: address the server in this run

The helper below receives an already running `ScenarioContext anvil` containing a Paper process named
`server`. It can be called from a JUnit test or an embedding application. No player is needed because
this operation uses the server console directly.

Place `ContextExample.java` in package `com.example.test` alongside the code that calls it.
Change the package declaration if your project uses a different package.

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;

import java.time.Duration;

final class ContextExample {
	static String announceReady(ScenarioContext anvil) {
		var console = anvil.processes().server("server").console();
		String marker = "context-ready-" + anvil.definition().getName();
		long checkpoint = console.checkpoint();
		console.sendCommand("say " + marker);
		return console.await(marker, checkpoint, Duration.ofSeconds(10));
	}
}
```

The context resolves `server` within its own scenario. The helper sends an announcement and returns
the newly captured console line containing its marker. Taking a checkpoint before the command means
an earlier identical line cannot satisfy the wait.

This establishes that the console produced the expected output after the command. It does not prove
that a player received the announcement; that needs a player's `Messages` capability, as shown in
[capabilities](../capabilities/index.md).

## Who starts and closes it?

The engine creates a context when it starts a scenario. An embedding application receives it from
`ScenarioEngine.start(...)` and owns closing that context, normally with try-with-resources. Closing
the engine also releases contexts it still owns.

With the [JUnit integration](../../../integrations/junit/index.md), the extension handles those
lifetimes and injects the context into your method. A class-level `@AnvilTest` still starts a fresh
execution for each test method. The context itself does not depend on Gradle or JUnit; those are ways
to arrange its creation and use.

The example helper borrows its context, so it leaves closing to the caller or integration. Closing
releases remaining players, process resources, and scenario workspace ownership. Do not save the
context, its players, or process handles for use after that point. Retaining files through a
persistent workspace does not preserve live runtime objects.

For integration-specific setup, see [JUnit context injection](../../../integrations/junit/selection/index.md)
or [embedding Anvil](../../../integrations/embedding/index.md).

Next: [Processes](../processes/index.md) explains the running server and proxy handles you access
through this context.
