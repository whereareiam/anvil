---
title: Protocol providers
description: Integrate another client library and expose its services through public contracts.
---

# External protocol providers and capabilities

An external backend owns its client library and connection lifecycle. Public capabilities describe
what scenario authors can do, while adapters translate those contracts into the chosen library's
operations. Anvil does not define a universal packet type or translate between unrelated libraries.

## Runtime integration

Provider contracts (`ProtocolProvider`, `ProtocolProviderRegistry`, `ProtocolBackend`, and
`ProtocolAuthentication`) live in `me.whereareiam.anvil.protocol.api.provider`. Player and composer
contracts (`ProtocolPlayer`, `ProtocolPlayerComposer`, and `ProtocolPlayerComposerProvider`) live
in `me.whereareiam.anvil.protocol.api.player`. Public values remain under `api.model` and `api.type`.

Implement `ProtocolProvider`, `ProtocolBackend`, and `ProtocolPlayer` using `protocol-api`, then
register the provider under `META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider`.
Add its artifact to `anvilProtocols`. One provider is selected per engine: the sole installed
provider is automatic, and multiple providers require `anvil { protocol("provider-id") }`.

The engine resolves the provider ID before discovering capabilities. A capability descriptor's
`supportedProtocolIds` selects its compatible backends; an empty set denotes backend-independent
behavior. Only one selected provider may contribute a particular capability type.

Define execution-service interfaces in the backend's own adapter API and expose them through
`ProtocolPlayer.findService`. A capability adapter obtains those interfaces from
`PlayerCapabilityContext.requireService`. Keep capability APIs dependent only on `anvil-api`, and
keep both backend and capability implementations dependent on shared contracts instead of each
other's implementation modules.

For example, an external movement adapter implements our `Movement` API through its own connection
contract. It does not import our `McProtocolMovementProvider` or worker implementation. Existing
MCProtocol capabilities continue to use their namespaced worker-operation and packet-listener SPI.
Installing another client library does not replace the codec of an existing MCProtocol connection.

### Package migration

Extensions using the former flat `me.whereareiam.anvil.protocol.api` package must update imports
to the provider or player packages above and recompile. Rename provider service descriptors to
`META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider`. Custom composer
registrations use `META-INF/services/me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider`.
Descriptor contents must match the implementation's fully qualified class name.

This is a source and binary compatibility change; old extension JARs must be rebuilt. Maven artifact
IDs, provider IDs, and worker operation/event names are unchanged. Packet-adapter package migration
is covered in [custom capabilities](../capabilities/index.md#mcprotocol-worker-adapter).

## Authentication

`ProtocolProvider.authentication(cacheDirectory)` returns an optional `ProtocolAuthentication`.
Offline-only providers inherit the empty implementation. Authentication is provider-owned and does
not require backend or player creation. Credentials stay inside the provider's private store and
must not appear in tooling inputs, arguments, environment variables, or output callbacks.

The Gradle tasks load the selected provider and its dependencies from `anvilProtocols` through the
resolvable `anvilProtocolRuntime` configuration. Use `anvilLogin --auth-profile=name` and
`anvilLogout --auth-profile=name`. `--profile` is Gradle's build-profiler option and is not an Anvil
account selector. The MCProtocol provider supplies its Microsoft account workflow through this API.

## Agent capabilities

`AgentDirectory` and `PlayerObservation` are supplied by the scenario runtime independently of the
selected client library. The built-in `Server` capability consumes observation directly and does
not require `Session`. A capability can use a backend service, agent operations, or both. Install
platform-side operation implementations as described in [agent extensions](../agent-operations/index.md).

## Verify your implementation

Test selection with both the default adapters and your own provider installed. Verify the portable
capability APIs through your connection service, missing-capability diagnostics, offline or optional
authentication behavior, and cleanup after failures. Include a real platform-agent journey when
your capability uses agent operations.

Anvil's [external-extension fixture](https://github.com/whereareiam/anvil/tree/dev/anvil-testing/testing-fixtures/fixtures-external-extension)
shows these public boundaries in a separately compiled JAR. Its backend is an in-process contract
fixture, not a second production Minecraft client. Instructions for running Anvil's internal
conformance suites belong in [Testing Anvil](../../contributing/testing/index.md).
