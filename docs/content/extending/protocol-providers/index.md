---
title: Overview
description: Integrate a native client library and expose services for capability implementations.
---

A protocol provider owns native client creation, verified version support, identity, and client
cleanup. Use this extension point to integrate another client library. A packet-backed capability
alone belongs under [Protocol adapters](../capabilities/protocol-adapters/index.md).

Create a Java library depending on `me.whereareiam.anvil:protocol-api` at the consumer's Anvil version.
Keep backend-specific service interfaces in a public adapter API if capability implementations in
other artifacts will consume them.

## Implement the runtime contracts

| Contract | Your implementation supplies |
|---|---|
| `ProtocolProvider` | Stable `id`, backend creation, optional authentication |
| `ProtocolBackend` | Verified support catalog, initially disconnected players, complete cleanup |
| `ProtocolPlayer` | Name, native version, identity, execution services, permanent destruction |

Provider contracts are in `me.whereareiam.anvil.protocol.api.provider`; player contracts are in
`me.whereareiam.anvil.protocol.api.player`. `PlayerRequest` and `ProtocolSupport` are in `api.model`.

For exact creation, service lookup, and cleanup contracts, read the
[ProtocolProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-protocol/protocol-api/src/main/java/me/whereareiam/anvil/protocol/api/provider/ProtocolProvider.java),
[ProtocolBackend](https://github.com/whereareiam/anvil/blob/dev/anvil-protocol/protocol-api/src/main/java/me/whereareiam/anvil/protocol/api/provider/ProtocolBackend.java), and
[ProtocolPlayer](https://github.com/whereareiam/anvil/blob/dev/anvil-protocol/protocol-api/src/main/java/me/whereareiam/anvil/protocol/api/player/ProtocolPlayer.java)
source Javadocs. These links use `dev`; select your release tag when checking a released version.

`ProtocolProvider.create(cacheDirectory, artifacts)` receives the private Anvil cache root and the
shared verified artifact resolver. `ProtocolBackend.create(PlayerRequest)` receives the exact
native version and target address plus the selected authentication mode and optional profile name.
Create a disconnected client; connection behavior is supplied through the appropriate capability.

Populate every `ProtocolSupport` entry with the exact Minecraft version, wire-protocol number,
library runtime identifier, binding family, minimum Java version, and supported protocol flags.
These flags describe backend support; capability-provider descriptors select capability adapters.

## Expose execution services

Implement stable interfaces for the operations your capability adapters need. Return them from
`ProtocolPlayer.findService(Class<T>)`. A provider then resolves that service through
`PlayerCapabilityContext.requireService(...)`.

Keep the interface meaningful for your backend. For example, an adapter API can expose typed client
commands while retaining packet-library details in its implementation. Do not depend on Anvil's
MCProtocol worker internals or reuse its opaque packet surface as a cross-library protocol format.

## Register and select the backend

Create `src/main/resources/META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider`
containing your provider's fully qualified class name. Add your artifact to the consumer's
`anvilProtocols` configuration.

This fragment goes in a consumer build that already applies Anvil's scenario tooling; the coordinates
and ID are examples for your published provider:

```kotlin
dependencies {
	anvilProtocols("com.example:example-protocol:1.0.0")
}

anvil {
	protocol("example")
}
```

The sole installed provider is selected automatically. If several are installed, an explicit ID is
required. The engine selects the provider before discovering capabilities, so adapters must declare
that same provider ID in `supportedProtocolIds` where their implementation depends on it.

## Verify selection and cleanup

Exercise automatic selection, explicit selection alongside MCProtocol, ambiguous selection, missing
execution services, and unsupported versions. Destroying a player must release its client resources;
closing the backend must attempt cleanup of all clients and any owned workers after failures.

The external-extension fixture in Anvil's test modules demonstrates discovery and service composition
through a separately compiled JAR. Its in-process backend is a contract fixture, not a production
Minecraft client. Use a real platform journey to verify a production backend's login and capability
behavior. See [Packaging and testing](../packaging/index.md).

Add an account workflow only when needed; [Authentication](./authentication/index.md) describes its
separate provider contract.
