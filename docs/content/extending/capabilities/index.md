---
title: Custom capabilities
description: Keep consumer APIs portable while implementing behavior through a backend service or platform agent.
---

# Custom capabilities

A capability family separates the consumer API, its implementation, and the wiring that selects
that implementation. Use your own Maven group and package namespace for external families.

| Artifact | Dependencies and ownership |
|---|---|
| `combat-api` | Public `Combat extends PlayerCapability`; depends on `anvil-api` |
| `combat-mcprotocol` | Host provider and worker adapter; depends on public APIs and MCProtocolLib |
| `combat-agent` | Alternative host adapter using shared agent-operation contracts |
| `combat` | Wiring artifact exposing the API and installing the chosen implementation |

Implementations do not depend on sibling implementations. If several adapters need a shared
execution contract, give that contract a clear API owner. Do not use a `common` implementation module
as a cross-module shortcut.

## Host provider

Implement `PlayerCapabilityProvider<Combat>` and register it under
`META-INF/services/me.whereareiam.anvil.capability.api.PlayerCapabilityProvider`.
Its `CapabilityDescriptor` supplies a globally unique ID, supported backend IDs, API version, and
required predecessor capabilities. An empty supported-backend set means backend-independent behavior.

The runtime orders providers by declared dependencies. `findCapability` and `requireCapability`
only expose declared predecessors. Obtain execution services with `context.requireService(...)`
and register player-scoped cleanup with `context.onDestroy(...)`.

Do not declare a `Session` dependency merely because most players happen to use it. Declare it when
the implementation actually needs the session capability. Agent-only behavior can be independent.

## MCProtocol worker adapter

Worker adapter, registry, operation, and packet-listener contracts live in
`me.whereareiam.anvil.protocol.adapter.api.capability`. `ProtocolPlayerConnection` and
`ProtocolWorkerPlayer` live in `me.whereareiam.anvil.protocol.adapter.api.player`.

Implement `ProtocolCapabilityAdapter` in the capability's own MCProtocol module and register it
under `META-INF/services/me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter`.
Its `install` method registers namespaced operations and packet listeners. Its `supports(protocolNumber)`
method declares compatible protocol bindings.

When a bundle carries multiple native API families, implement `ProtocolCapabilityAdapterProvider`
and register it under
`META-INF/services/me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterProvider`.
Its constructor and `supports(protocolNumber)` must not touch native packet classes. `create()` is
called only after selection, so an incompatible adapter is never loaded merely to ask whether it
supports a version. Keep packet behavior in the capability-owned adapter modules. Direct adapter
registrations remain supported for existing single-family extensions.

The host provider obtains `ProtocolPlayerConnection`, sends named requests, and subscribes to worker
events. The worker uses MCProtocolLib packets and the shared worker execution surface. This adds
behavior without editing the core worker dispatch. It does not replace the backend's packet codec
or install support for arbitrary new library versions.

Keep operation/event names globally unique and stable. Preserve external dependency classpath
entries when testing against isolated workers. The selected protocol JAR and its own library
dependencies precede host entries; do not rely on another version's Netty, NBT, or Adventure classes.
Operation names must be qualified with a namespace (for example `combat.attack` or `combat:attack`).
The worker reserves `create`, `destroy`, and `shutdown` for its own lifecycle; adapter registration
rejects those names. Use the public connection API instead of constructing private worker envelopes.

Extensions using the former flat `me.whereareiam.anvil.protocol.adapter` package must update their
imports, rename the adapter service descriptor to the path above, and recompile. The artifact remains
`protocol-adapter-api`; capability operation/event names are unchanged. Do not import
`mcprotocol.worker.host` or `mcprotocol.worker.child` implementation classes.

## Alternative backends and agents

For another packet library, implement the same consumer capability through that backend's own
public connection-service interface. See [protocol providers](../protocol-providers/index.md).

For native server/proxy behavior, define typed agent operations and a host adapter that uses
`AgentDirectory`. The operation implementation lives in the platform process, while the public
capability remains independent of platform SDK types. See [agent operations](../agent-operations/index.md).

## Wire and test the family

The consumer adds the wiring artifact to `anvilCapabilities`. Avoid installing two selected providers
for the same capability type. Test declared-dependency failures, unsupported backends, operation
registration, player cleanup, and observable runtime behavior. Anvil's separately packaged
external-extension fixture demonstrates the public boundaries without importing engine internals.
