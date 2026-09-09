---
title: Public contracts
description: Choose a capability owner and define a typed API without backend or platform types.
---

Choose the owner before writing the capability interface. Behavior concerning one simulated player
extends `PlayerCapability`. Behavior concerning a server or proxy extends `ProcessCapability` and
is retrieved directly from its `RunningProcess`. Both use the shared `Capability` identity contract.

The Echo example belongs to a process: it sends a request to its native handler without using a player.
Create an `echo-api` Java library with a dependency on `me.whereareiam.anvil:api` at your consumer's
Anvil version. Add JetBrains annotations to the compile classpath. Keep provider, transport,
packet-library, and platform SDK types out of this public API.

Place this complete interface in `src/main/java/com/example/echo/Echo.java`:

```java
package com.example.echo;

import me.whereareiam.anvil.api.process.ProcessCapability;
import org.jetbrains.annotations.NotNull;

/**
 * Executes a round trip through an extension installed in the owning process.
 */
public interface Echo extends ProcessCapability {
	/**
	 * Returns the response produced by this process's echo handler.
	 *
	 * @param message text sent to the handler
	 * @return handler response
	 */
	@NotNull String send(@NotNull String message);
}
```

The caller selects the process when retrieving `Echo`. Each instance already knows its owner,
so the channelOperation needs only the message. The public interface does not require an agent;
that is the implementation chosen by this example.

## Make behavior explicit

For a production capability, document whether a method sends a request, waits for an acknowledgement,
or observes completed application behavior. State timeout and connection requirements where they
matter. A successful packet send alone does not establish that the server accepted the action.

Keep reusable public request and result models in the capability's `model` package and enums under
`type`. Use meaningful domain values rather than exposing packets or serialized payloads. Add
separate top-level model files when the public contract needs them.

## Use it in a journey

After installing the [host provider](../agent-adapters/index.md) and [agent handler](../../agent-operations/index.md),
this fragment belongs inside a test with a running `ScenarioContext anvil` and a process named
`lobby` that exposes a platform agent:

```java
import com.example.echo.Echo;

import static org.junit.jupiter.api.Assertions.assertEquals;

var lobby = anvil.processes().server("lobby");
assertEquals("echo:hello", lobby.capability(Echo.class).send("hello"));
```

This checks the external handler's response. No simulated player is needed. If your feature instead
observes a particular player's state, define a `PlayerCapability` and provide the corresponding
player connection and observation requirements.
