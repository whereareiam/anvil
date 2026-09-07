---
title: Contracts and handlers
description: Define a shared request descriptor and register its platform implementation.
---

This example creates an operation that returns `echo:` followed by the supplied string. Use two
artifacts: `echo-operations` for the shared contract and `echo-agent` for the handler. Both compile
against `me.whereareiam.anvil:agent-api` at the same version as the host.

## Define the shared operation

Place this in `echo-operations/src/main/java/com/example/echo/operation/EchoOperations.java`:

```java
package com.example.echo.operation;

import me.whereareiam.anvil.agent.api.model.AgentOperation;

/**
 * Shared host and agent descriptors for the echo extension.
 */
public final class EchoOperations {
	/**
	 * Returns the handler's echo response for a string request.
	 */
	public static final AgentOperation<String, String> ECHO = AgentOperation.<String, String>builder()
			.name("com.example.echo.message")
			.requestType(String.class)
			.responseType(String.class)
			.build();
}
```

Use a name beginning with the provider ID followed by a dot. Request and response types must be
supported by the selected transport. The bundled transport uses Jackson; immutable custom models
can use Lombok `@Builder` with `@Jacksonized`. Keep platform types out of serialized models.

## Register a handler

Place this in `echo-agent/src/main/java/com/example/echo/agent/EchoAgentOperations.java`:

```java
package com.example.echo.agent;

import com.example.echo.operation.EchoOperations;
import me.whereareiam.anvil.agent.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.api.operation.AgentOperationRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Installs the echo operation into a platform agent.
 */
public final class EchoAgentOperations implements AgentOperationProvider {
	@Override
	public @NotNull String id() {
		return "com.example.echo";
	}

	@Override
	public void install(@NotNull AgentOperationRegistry registry) {
		registry.register(EchoOperations.ECHO,
				(platform, request) -> platform.call(() -> "echo:" + request));
	}
}
```

Create `src/main/resources/META-INF/services/me.whereareiam.anvil.agent.api.operation.AgentOperationProvider`
in the handler artifact containing:

```text
com.example.echo.agent.EchoAgentOperations
```

Registration is limited to `install`. Duplicate provider IDs, duplicate operation names, and names
outside the provider's namespace fail registration. The platform's built-in operations remain
available alongside your extension.

## Call native APIs

A native handler can resolve a service with `platform.requireService(...)`. For example, the Bukkit
agent exposes `org.bukkit.Server`. Execute native work through `platform.call(...)` and obey any
additional threading rules of the API you invoke. Compile platform SDKs as `compileOnly` dependencies.

Override `supports(AgentInfo)` for handlers restricted to a platform or role. Install platform-specific
JARs only into compatible processes so their classes can link against the expected native SDK.
The generic Echo handler above uses no platform-specific types.

Next, [package and install the handler](../installation/index.md).
