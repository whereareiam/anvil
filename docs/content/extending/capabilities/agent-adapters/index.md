---
title: Agent adapters
description: Implement a player capability with scenario-owned connections to platform agents.
---

Use an agent adapter when the operation needs server or proxy APIs. Keep native code in the
[agent handler](../../agent-operations/contracts/index.md); the host adapter calls its shared typed
operation and exposes the public capability.

The following complete provider uses `Echo` from [Public contracts](../contracts/index.md) and
`EchoOperations` from [Contracts and handlers](../../agent-operations/contracts/index.md). Place it
in `echo-host/src/main/java/com/example/echo/host/EchoProvider.java`. The host artifact depends on
`echo-api`, `echo-operations`, `capability-api`, and `agent-api`.

```java
package com.example.echo.host;

import com.example.echo.Echo;
import com.example.echo.operation.EchoOperations;
import me.whereareiam.anvil.agent.api.transport.AgentDirectory;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Provides echo calls through the running scenario's platform agents.
 */
public final class EchoProvider implements PlayerCapabilityProvider<Echo> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("com.example.echo").build();
	}

	@Override
	public @NotNull Class<Echo> capability() {
		return Echo.class;
	}

	@Override
	public @NotNull Echo create(@NotNull PlayerCapabilityContext context) {
		AgentDirectory agents = context.requireService(AgentDirectory.class);
		return (process, message) -> agents.require(process)
				.request(EchoOperations.ECHO, message);
	}
}
```

Add `src/main/resources/META-INF/services/me.whereareiam.anvil.capability.api.PlayerCapabilityProvider`
containing:

```text
com.example.echo.host.EchoProvider
```

The descriptor has no predecessor capabilities and no protocol restriction because this provider
only uses the scenario's `AgentDirectory` service.

## Ownership and process changes

The directory is keyed by scenario process name and contains processes that expose an agent. Borrow
its clients for requests; the scenario closes them. Resolve the current client through the directory
when you need it, particularly across a process restart.

Use `context.onDestroy(...)` for resources your capability owns, such as an external listener
registration. Do not close borrowed agent clients in that callback.

## Verify the round trip

Install the host dependency in `anvilCapabilities`, install the handler JAR as a workspace asset, and
run the assertion on the [public contract page](../contracts/index.md). See
[Installing agent operations](../../agent-operations/installation/index.md) for the workspace path.

If capability lookup fails, inspect the host service descriptor and dependency selection. If the
capability exists but its request fails, check the process name, platform-agent readiness, handler
service descriptor, and the target process's `anvil-console.log`.
