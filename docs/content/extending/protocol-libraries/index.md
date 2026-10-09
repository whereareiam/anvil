---
title: Overview
description: Integrate another native client library as a protocol library with its own releases, players, and services.
---

A protocol library owns native client creation, the releases it can run, client identity, and client
cleanup. MCProtocolLib is the bundled library, with the ID `mcprotocol`. Use this extension point to
integrate another client library. A packet-backed capability alone belongs under
[Protocol adapters](../capabilities/protocol-adapters/index.md).

Several libraries can be installed at once; Anvil selects one for each player. Create a Java library
depending on `me.whereareiam.anvil:protocol-api` at the consumer's Anvil version. Keep
library-specific service interfaces in a public API artifact if capability implementations in other
artifacts will consume them.

## Implement the runtime contracts

| Contract | Your implementation supplies |
|---|---|
| `ProtocolLibraryProvider` | Stable `id()`, release data through `releases(context)`, the run-scoped library through `create(context)`, and optional `authentication(accountsDirectory)` |
| `ProtocolLibrary` | Its `releases()`, initially disconnected players through `create(PlayerRequest)`, and cleanup of every client and worker in `close()` |
| `ProtocolPlayer` | Name, client version, `libraryId()`, the selected `release()`, identity, an optional capability channel, library services, and permanent destruction |

`ProtocolLibraryProvider`, `ProtocolLibrary`, `ProtocolArtifactResolver`, and `ProtocolAuthentication`
are in `me.whereareiam.anvil.protocol.api.library`. Player contracts are in
`me.whereareiam.anvil.protocol.api.player`. `ProtocolLibraryContext`, `ProtocolRelease`, and
`PlayerRequest` are in `me.whereareiam.anvil.protocol.api.model`; `ProtocolFeature` is in
`me.whereareiam.anvil.protocol.api.type`.

For the exact contracts, read the
[ProtocolLibraryProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-protocol/protocol-api/src/main/java/me/whereareiam/anvil/protocol/api/library/ProtocolLibraryProvider.java),
[ProtocolLibrary](https://github.com/whereareiam/anvil/blob/dev/anvil-protocol/protocol-api/src/main/java/me/whereareiam/anvil/protocol/api/library/ProtocolLibrary.java),
[ProtocolRelease](https://github.com/whereareiam/anvil/blob/dev/anvil-protocol/protocol-api/src/main/java/me/whereareiam/anvil/protocol/api/model/ProtocolRelease.java), and
[ProtocolPlayer](https://github.com/whereareiam/anvil/blob/dev/anvil-protocol/protocol-api/src/main/java/me/whereareiam/anvil/protocol/api/player/ProtocolPlayer.java)
source Javadocs. These links use `dev`; select your release tag when checking a released version.

## Describe the releases

`releases(context)` returns every release your library can run as `ProtocolRelease` values. Anvil
reads it to select a library before any library exists, so keep it cheap and free of side effects. A
release speaks exactly one wire protocol and lists every Minecraft version that speaks it:

```java
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;

ProtocolRelease release = ProtocolRelease.builder()
		.libraryVersion("2.4.0")
		.minecraftVersion(MinecraftVersion.parse("1.20.5"))
		.minecraftVersion(MinecraftVersion.parse("1.20.6"))
		.verifiedVersion(MinecraftVersion.parse("1.20.6"))
		.protocolNumber(766)
		.javaVersion(17)
		.feature(ProtocolFeature.ONLINE_AUTHENTICATION)
		.build();
```

The library version is your release identifier, such as the client library version. The highest
listed Minecraft version is the release key returned by `version()`. List a version under
`verifiedVersion` only when your own live tests run it. `javaVersion` is the minimum Java feature
version of your client runtime. `ONLINE_AUTHENTICATION` declares that clients of the release can log
in to online-mode servers; features describe the client and never select capability providers. Each
Minecraft version may appear in only one of your releases.

`release.support(version)` assesses one version: `UNSUPPORTED` when the release does not list it,
`UNTESTED` for a user-supplied release, `VERIFIED` for a verified version, and `COMPATIBLE`
otherwise. Mark a release that cannot run with `launchRefusal(reason)`, for example when one of its
artifacts has no pinned checksum yet. Anvil still lists it, but refuses every player that selects it
with that reason.

`ProtocolLibraryContext` supplies the Anvil cache root, in which your library owns its own
subdirectory, the account directory, and a `ProtocolArtifactResolver`. The resolver's
`resolve(uri, destination, sha256)` operation supplies one exact, checksum-verified artifact, such as
a native runtime JAR; it does not expose generic acquisition or cache APIs. `getAdditionalReleases()`
returns the file a user configured for your library ID through `protocolReleases`, or `null`. Read it
in your own release-data format and mark its releases with `additional(true)`, so they are assessed
as `UNTESTED`. See [Versions and compatibility](../../building-blocks/environments/platforms/versions/index.md#add-release-data)
for how users supply that file.

## Create players

The engine creates one `ProtocolLibrary` per library when the first player selects it and closes it
with the engine. `ProtocolLibrary.create(PlayerRequest)` receives the player name, the exact
`clientVersion`, the selected `release`, the target address, the authentication mode, and the
optional account ID. Create a disconnected client; connection behavior is supplied through the
appropriate capability. Destroying a player releases its client resources, and `close()` attempts
cleanup of all clients and owned workers even after a failure.

## Understand library selection

Anvil selects a library and an exact release for each player:

1. The player's `protocolLibrary`, else the scenario's, else the engine's. A player created from an
   account lease uses the library that stores the leased account.
2. Without a declaration, every installed library that lists the player's Minecraft version is ranked
   by support level, and the strongest one wins. A tie at the strongest level is refused with a
   message naming the tied libraries and asking for `protocolLibrary`.
3. The release is the one that lists the exact version; a neighboring release is never used.
4. The support policy applies to that release's level, and the release must be launchable.

An unknown library ID is refused with the installed IDs. The engine checks its own library ID, and
every library with additional release data, when it is created; it checks a scenario's library before
any of the scenario's processes start. The [versions page](../../building-blocks/environments/platforms/versions/index.md)
describes the policies from a test author's view.

## Expose execution services

For typed worker operations, expose your protocol API's `ProtocolChannel` through
`ProtocolPlayer.channel()`. Launcher binding supplies the corresponding capability-owned channel to
`ProtocolPlayerCapabilityContext.channel()`. The library owns serialization and transport; feature
wiring owns operation descriptors and the public feature API owns domain payload models.

The channel's `installedCapabilities()` and `unavailableCapabilities()` report what the player's
native worker installs and why the rest is missing. Composition skips every protocol-backed provider
whose capability the worker does not install, together with the providers that depend on it, and
reports the worker's reason, or `not installed by the <library> worker`, when the capability is
requested. A player without a channel composes every provider that accepts its library.

A native worker loads `NativeWorkerProvider` implementations through `ServiceLoader` and calls
`create(NativeWorkerContext)` once per worker, with the library ID, the release key version, the
protocol number, and the native session type. Anvil's launcher supplies the provider that installs
capability `WorkerExtension` bindings. The returned `NativeWorkerExtension` binds each player through
`bind(NativePlayer, NativeOperations)`: your `NativePlayer<S>` supplies the current native session,
generation-scoped listener registration through `bindNativeSession(...)`, and the release-specific
adapter of a port through `adapter(Class)`. MCProtocol's worker transport and
dispatch remain private implementation details of that library.

For another library-specific service, implement a stable interface and return it from
`ProtocolPlayer.findService(Class<T>)`. A provider can resolve that service through
`ProtocolPlayerCapabilityContext.requireService(...)`, and declares your library ID in
`supportedLibraries()`. Player observations use the shared `PlayerCapabilityContext.observation()`
contract. Providers that need only those observations and capability dependencies use
`PlayerCapabilityProvider` from `capability-api`; they do not need a protocol-specific context.
Agent-backed player factories use `AgentPlayerCapabilityProvider` and `AgentPlayerCapabilityContext`
from `capability-agent-api`; they request native work through `channel(processName)`. Process
capabilities backed by agents use `AgentProcessCapabilityProvider` and its own-process `channel()`,
independently of protocol player composition.

Keep the interface meaningful for your library. For example, an adapter API can expose typed client
commands while retaining packet-library details in its implementation.

## Compose the public player facade

The installed `ProtocolPlayerComposer` wraps a library-owned `ProtocolPlayer` as a public
`SimulatedPlayer`. A custom composer implements this signature:

```java
@NotNull SimulatedPlayer compose(
		@NotNull ProtocolPlayer player,
		@NotNull PlayerObservation observation,
		@Nullable PresentationMetadata metadata,
		@NotNull Consumer<SimulatedPlayer> onDestroyed
);
```

`ProtocolPlayer` belongs to `me.whereareiam.anvil.protocol.api.player`. `PlayerObservation` and
`SimulatedPlayer` belong to `me.whereareiam.anvil.api.player`; `PresentationMetadata` belongs to
`me.whereareiam.anvil.api.model`. The destruction callback uses `java.util.function.Consumer`,
with JetBrains nullability annotations on the signature.

The protocol player manager passes metadata from `PlayerOptions` alongside the native player and
its scoped `PlayerObservation`. Preserve that optional value in `SimulatedPlayer.metadata()` while
retaining the library's connection identity. The launcher binds the bundled capability composer
to its scoped request channels; a composer does not receive an untyped collection of scenario services.
Invoke `onDestroyed` after permanent player destruction, not on an ordinary disconnect.
`ProtocolPlayerComposerProvider.create()` supplies one composer for players of every installed
library; it selects library-specific behavior from `ProtocolPlayer.libraryId()`.

## Register the library

Create `src/main/resources/META-INF/services/me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider`
containing your provider's fully qualified class name. Add your artifact to the consumer's
`anvilRuntimeOnly` configuration.

This fragment goes in a consumer build that already applies Anvil's scenario tooling; the coordinates
and ID are examples for your published library:

```kotlin
dependencies {
	add("anvilRuntimeOnly", "com.example:example-protocol:1.0.0")
}

anvil {
	engine {
		protocolLibrary("example")
	}
}
```

`protocolLibrary("example")` makes your library the engine default; without it, each player uses
the installed library with the strongest support for its version. Protocol-backed capability
adapters declare your ID in `supportedLibraries()` where their implementation depends on it; an
empty set accepts every library.

## Verify selection and cleanup

Exercise automatic selection alone and alongside MCProtocol, an explicit choice, a tie, an unknown
ID, missing execution services, unsupported versions, and a release that cannot be launched.
Destroying a player must release its client resources; closing the library must attempt cleanup of
all clients and any owned workers after failures.

The external-extension fixture in Anvil's test modules demonstrates discovery and service composition
through a separately compiled JAR. Its in-process library is a contract fixture, not a production
Minecraft client. Use a real platform journey to verify a production library's login and capability
behavior. See [Packaging and testing](../packaging/index.md).

Add an account workflow only when needed; [Authentication](./authentication/index.md) describes its
separate library service.
