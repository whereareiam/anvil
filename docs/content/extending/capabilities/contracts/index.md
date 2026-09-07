---
title: Public contracts
description: Define a capability API that test authors can use without backend or platform types.
---

Create an `echo-api` Java library with a dependency on `me.whereareiam.anvil:anvil-api` at your
consumer's Anvil version. Add JetBrains annotations to the compile classpath. Keep this module free
of capability-provider, agent, packet-library, and platform SDK dependencies.

Place this complete interface in `src/main/java/com/example/echo/Echo.java`:

```java
package com.example.echo;

import me.whereareiam.anvil.api.player.PlayerCapability;
import org.jetbrains.annotations.NotNull;

/**
 * Executes a round trip through an extension installed in a managed process.
 */
public interface Echo extends PlayerCapability {
	/**
	 * Returns the response produced by the selected process's echo handler.
	 *
	 * @param process scenario process name
	 * @param message text sent to the handler
	 * @return handler response
	 */
	@NotNull String send(@NotNull String process, @NotNull String message);
}
```

The process name is a scenario name such as `lobby`, not a hostname. The implementation resolves
that name through the scenario's agent directory.

## Make behavior explicit

For a production capability, document whether a method sends a request, waits for an acknowledgement,
or observes completed application behavior. State timeout and connection requirements where they
matter. A successful packet send alone does not establish that the server accepted the action.

Keep reusable public request and result models in the capability's `model` package and enums under
`type`. Use meaningful domain values rather than exposing packets or serialized payloads. Add
separate top-level model files when the public contract needs them.

## Use it in a journey

After installing the [host adapter](../agent-adapters/index.md) and [agent handler](../../agent-operations/index.md),
this fragment belongs inside a test with an existing `SimulatedPlayer player` and a process named
`lobby`:

```java
import com.example.echo.Echo;

import static org.junit.jupiter.api.Assertions.assertEquals;

assertEquals("echo:hello", player.capability(Echo.class).send("lobby", "hello"));
```

This checks the external handler's response. Echo itself does not require a connected game session.
A capability that reads player state should additionally verify the relevant connection and
server-observed state.
