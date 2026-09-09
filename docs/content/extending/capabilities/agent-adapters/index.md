---
title: Agent providers
description: Implement process or player capabilities through native agent operations.
---

Use an `AgentProcessCapabilityProvider` to implement a process capability through its server or proxy
agent. The process owns the capability; the provider sends typed requests through a borrowed
`RequestChannel`. Native SDK code belongs in the
[handler](../../agent-operations/contracts/index.md) installed inside the managed process.

## Implement the process capability

The following complete provider uses `Echo` from [Public contracts](../contracts/index.md) and
`EchoOperations` from [Contracts and handlers](../../agent-operations/contracts/index.md). Place it
in `echo-host/src/main/java/com/example/echo/host/EchoProvider.java`. The host artifact depends on
`echo-api`, `echo-operations`, and `me.whereareiam.anvil:capability-agent-api` at the consumer's Anvil
version. The shared operation artifact exports `agent-api` for its descriptor types; the capability
API itself exposes no agent client or native handler contracts.

```java
package com.example.echo.host;

import com.example.echo.Echo;
import com.example.echo.channelOperation.EchoOperations;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Provides echo calls through the owning process's platform agent.
 */
public final class EchoProvider implements AgentProcessCapabilityProvider<Echo> {
	private static final ChannelOperation<String, String> ECHO = new ChannelOperation<>(
			EchoOperations.ECHO.getName(),
			EchoOperations.ECHO.getRequestType(),
			EchoOperations.ECHO.getResponseType()
	);

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("com.example.echo").build();
	}

	@Override
	public @NotNull Class<Echo> capability() {
		return Echo.class;
	}

	@Override
	public @NotNull Echo create(@NotNull AgentProcessCapabilityContext context) {
		RequestChannel channel = context.channel();
		return message -> channel.request(ECHO, message);
	}
}
```

The host channel descriptor derives its name and payload types from the shared `AgentOperation`
once. Both ends therefore use the same operation contract without duplicating its ID or schemas.
`RequestChannel` supplies typed request/response calls; player events and worker capability discovery
belong to `capability-protocol-api`. Use a `Void` request schema with a `null` request for an
operation without input. The operation's contract determines whether its response may be `null`.

Add `src/main/resources/META-INF/services/me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider`
containing:

```text
com.example.echo.host.EchoProvider
```

The factory receives the process name and platform ID as well as its request channel. Override
`supportsPlatform(platformId)` when your implementation requires a specific platform. Keep that
selection aligned with the platforms supported by your installed native handlers.

## Compose behavior and own resources

Declare process capability dependencies in the `CapabilityDescriptor` returned by `descriptor()`,
using `.requiredCapability(Type.class)` on its builder. Retrieve them with
`context.requireCapability(Type.class)`. Only declared predecessors are available, and all belong
to this same logical process. Register capability-owned resources with `context.onClose(...)`;
cleanup runs in reverse registration order before the underlying agent connection closes.

The scenario owns the request channel's transport, which follows the process's replacement
connection across restarts. The same capability instance remains associated with that process;
requests can fail while its agent is disconnected. Native handler code is loaded again with each
managed JVM. Final scenario cleanup closes these capabilities after players and before their
transports. After cleanup, process capability lookup fails and `hasCapability(...)` returns `false`.

## Implement player behavior through agents

Use `AgentPlayerCapabilityProvider<C extends PlayerCapability>` when the capability belongs to one
player but obtains its behavior through agents. This has the same owner scope as the built-in
`Server` capability, which consumes player observations and works independently of `Session`.

Its `AgentPlayerCapabilityContext` extends the shared `PlayerCapabilityContext`: player name and
version, observations, declared dependencies, and cleanup. It adds `channel(processName)` for native
requests. Select a scenario process name to request work from its agent; use player observations
to determine the relevant backend or proxy. Return the provider's
identity and dependencies from `descriptor()`, and register owned cleanup with `context.onClose(...)`.

Register such a factory under
`META-INF/services/me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider`.
Both provider kinds use `capability-agent-api` and its shared `capability-api` contracts.
Process contracts live under `capability.agent.api.process`, and player contracts under
`capability.agent.api.player`. Their scope determines
where the consumer retrieves the result:

| Provider | Consumer lookup | Cleanup lifetime |
|---|---|---|
| `AgentProcessCapabilityProvider` | `process.capability(Type.class)` | Logical process within the scenario |
| `AgentPlayerCapabilityProvider` | `player.capability(Type.class)` | Simulated player |

## Verify the round trip

Install the host dependency in `anvilCapabilities`, install the handler JAR as a workspace asset, and
run the assertion on the [public contract page](../contracts/index.md). See
[Installing agent operations](../../agent-operations/installation/index.md) for the workspace path.

If capability lookup fails, inspect the host service descriptor and provider selection. If the
capability exists but its request fails, check platform-agent readiness, the handler service
descriptor, and the target process's `anvil-console.log`.
