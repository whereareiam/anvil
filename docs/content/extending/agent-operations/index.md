---
title: Agent operations
description: Extend platform agents with typed operations and keep native code outside consumer APIs.
---

# External agent operations

Agent operations execute inside the managed server or proxy. An external library supplies their
shared contracts, platform handlers, and host capability adapter without changing Anvil sources.
The host and platform communicate through the existing authenticated loopback connection.

## Shared operation contract

Put descriptors and request/response models in an operation-contract artifact depending on
`me.whereareiam.anvil:agent-api`. Keep the consumer-facing player capability API separate: it depends
only on `anvil-api` and does not expose agent or platform types.

```java
package com.example.echo;

import me.whereareiam.anvil.agent.api.model.AgentOperation;

public final class EchoProtocol {
    public static final AgentOperation<String, String> ECHO = AgentOperation.<String, String>builder()
            .name("com.example.echo.message")
            .requestType(String.class)
            .responseType(String.class)
            .build();
}
```

Request and response classes must be supported by the selected transport. The bundled JSON transport
uses Jackson. Immutable custom models can use Lombok `@Builder` and `@Jacksonized`. The descriptor's
response may be null when an operation deliberately returns no result.

## Platform implementation

Implement the public operation SPI and register the shared descriptor:

```java
package com.example.echo;

import me.whereareiam.anvil.agent.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.api.operation.AgentOperationRegistry;
import org.jetbrains.annotations.NotNull;

public final class EchoAgentOperations implements AgentOperationProvider {
    @Override
    public @NotNull String id() {
        return "com.example.echo";
    }

    @Override
    public void install(@NotNull AgentOperationRegistry registry) {
        registry.register(EchoProtocol.ECHO, (platform, request) -> "echo:" + request);
    }
}
```

Create `META-INF/services/me.whereareiam.anvil.agent.api.operation.AgentOperationProvider` containing
`com.example.echo.EchoAgentOperations`. Each operation name begins with its provider ID and a dot.
Duplicate providers, duplicate operation names, foreign namespaces, and registration outside the
installation call are rejected. The built-in ping, identity, and command operations remain available.

Native behavior belongs in the platform implementation artifact. A Bukkit handler can resolve
`org.bukkit.Server` with `platform.requireService(Server.class)` and execute its work with
`platform.call(() -> ...)`; Bukkit dispatches that work onto its main thread. Velocity and BungeeCord
expose their proxy APIs. Use `supports(AgentInfo)` and install platform-specific JARs only into the
processes they support. Implementations are responsible for observing any additional threading rules
of the native API they call.

## Install through workspace assets

Register the extension's built JAR with the existing artifact registry:

```kotlin
anvil {
    artifact("echo-agent", project(":echo-agent"))
}
```

Declare it in each target server or proxy's workspace:

```java
WorkspaceAsset.builder()
        .group("echo-agent")
        .source(AssetSource.artifact("echo-agent"))
        .target(Path.of("plugins", "anvil-agent-extensions", "echo.jar"))
        .build()
```

The platform agent loads JARs from that directory in filename order with its own class loader as the
parent. Package the operation contract and implementation dependencies in the extension JAR, or
install dependency JARs in the same directory. Do not bundle Anvil agent APIs or the platform SDK;
their identities come from the parent loader. Merely adding a dependency to `anvilCapabilities`
does not install it into a remote JVM. The endpoint closes the extension loader when it shuts down
and releases it if startup fails.

## Host capability adapter

Inside `PlayerCapabilityProvider.create`, resolve the scenario's directory of borrowed clients:

```java
AgentDirectory agents = context.requireService(AgentDirectory.class);
String reply = agents.require("lobby").request(EchoProtocol.ECHO, "hello");
```

The directory is keyed by scenario process name and contains only processes with an agent. Keep this
transport access inside the host adapter and expose a normal `PlayerCapability` to scenario authors.
The adapter depends on public APIs and the shared operation contract, not the platform-handler
implementation. Register the host adapter through the capability SPI and add its wiring artifact to
`anvilCapabilities` as usual. Clients are owned and closed by the scenario; capability adapters must
not close them.