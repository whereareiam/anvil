---
title: Agent providers
description: Implement process capabilities through native agent operations.
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
import com.example.echo.operation.EchoOperations;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
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
belong to the player's `ProtocolChannel` in `protocol-api`. Use a `Void` request schema with a `null` request for an
operation without input. The operation's contract determines whether its response may be `null`.

Add `src/main/resources/META-INF/services/me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider`
containing:

```text
com.example.echo.host.EchoProvider
```

Process contracts live under `capability.agent.api.process`, alongside the shared `capability-api`
contracts. A test retrieves the capability with `process.capability(Type.class)`, and it lives as long as the
logical process within the scenario. The factory receives the process name and platform ID as well as its
request channel. Override
`supportsPlatform(platformId)` when your implementation requires a specific platform. Keep that
selection aligned with the platforms supported by your installed native handlers.

## Compose behavior and own resources

Declare process capability dependencies in the `CapabilityDescriptor` returned by `descriptor()`,
using `.requiredCapability(Type.class)` on its builder. Retrieve them with
`context.requireCapability(Type.class)`. A capability it can work without goes in
`.optionalCapability(Type.class)` instead: it is created first when the process has it, and
`context.findCapability(Type.class)` is empty when it does not. Only declared predecessors are
available, and all belong to this same logical process. Register capability-owned resources with
`context.onClose(...)`; cleanup runs in reverse registration order before the underlying agent
connection closes.

The scenario owns the request channel's transport, which follows the process's replacement
connection across restarts. The same capability instance remains associated with that process;
requests can fail while its agent is disconnected. Native handler code is loaded again with each
managed JVM. Final scenario cleanup closes these capabilities after players and before their
transports. After cleanup, process capability lookup fails and `hasCapability(...)` returns `false`.

## Put what a server knows about a player on the process

An agent-backed provider always belongs to a process. What a client can do or see is a player capability
backed by its protocol. What a server or proxy knows about a player, such as the backend a proxy connected it
to, is a capability of that process, which a test reads from the process:

```java
Players lobby = scenario.processes().server("lobby").capability(Players.class);
```

## Use the platform's general facts

Every platform agent answers a few general operations for every capability, so a capability does not need an
extension for them. A process capability reaches them through `context.platform()`, a `ProcessPlatform`
(`me.whereareiam.anvil.capability.api.platform`): `execute(command)` runs a console command and returns whether
the platform accepted it, and `events()` lists the players' joins and leaves the process reported, as
`PlatformEvent` values with the process and whether it is a proxy, a position, when it happened, `JOINED` or
`LEFT`, the player as the platform reports it, and on a proxy the backend, or none for the proxy itself. The
built-in Dispatch capability is one such provider:

```java
@Override
public @NotNull Dispatch create(@NotNull AgentProcessCapabilityContext context) {
	return command -> context.platform().execute(command);
}
```

## Install the handlers with the provider

A provider can declare the agent extension JARs its capability needs, so that every scenario installs them
without a workspace declaration. Return them from `agentExtensions()`, each with the platform IDs of the
processes that load it and a class the JAR contains:

```java
@Override
public @NotNull Set<AgentExtension> agentExtensions() {
	return Set.of(AgentExtension.builder()
			.platform(Platforms.PAPER)
			.platform(Platforms.SPIGOT)
			.entrypoint("com.example.echo.agent.bukkit.EchoBukkitOperations")
			.build());
}
```

`AgentExtension` belongs to `me.whereareiam.anvil.capability.agent.api.model` and `Platforms` to
`me.whereareiam.anvil.api.type`. Put the handler JAR on the runtime class path of the tests, for example in
`anvilRuntimeOnly`: Anvil finds it there by the class and copies it into `plugins/anvil-agent-extensions` of
every process of those platforms before the process starts. Build one JAR per platform agent when the
handlers use a platform's SDK, so each process links only against its own.

## Verify the round trip

Install the host dependency in `anvilImplementation`, install the handler JAR as a workspace asset or
declare it through `agentExtensions()`, and
run the assertion on the [public contract page](../contracts/index.md). See
[Installing agent operations](../../agent-operations/installation/index.md) for the workspace path.

If capability lookup fails, inspect the host service descriptor and provider selection. If the
capability exists but its request fails, check platform-agent readiness, the handler service
descriptor, and the target process's `anvil-console.log`.
