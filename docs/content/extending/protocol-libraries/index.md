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
| `ProtocolPlayer` | Name, client version, `libraryId()`, the selected `release()`, identity, an optional typed player channel, library services, and permanent destruction |

`ProtocolLibraryProvider`, `ProtocolLibrary`, `ProtocolArtifactResolver`, and `ProtocolAuthentication`
are in `me.whereareiam.anvil.protocol.api.library`. Player contracts are in
`me.whereareiam.anvil.protocol.api.player`. `ProtocolLibraryContext`, `ProtocolRelease`, and
`PlayerRequest` are in `me.whereareiam.anvil.protocol.api.model`; `ProtocolFeature` is in
`me.whereareiam.anvil.protocol.api.type`.

For the exact contracts, read the
[ProtocolLibraryProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-player/player-api/src/main/java/me/whereareiam/anvil/protocol/api/library/ProtocolLibraryProvider.java),
[ProtocolLibrary](https://github.com/whereareiam/anvil/blob/dev/anvil-player/player-api/src/main/java/me/whereareiam/anvil/protocol/api/library/ProtocolLibrary.java),
[ProtocolRelease](https://github.com/whereareiam/anvil/blob/dev/anvil-player/player-api/src/main/java/me/whereareiam/anvil/protocol/api/model/ProtocolRelease.java), and
[ProtocolPlayer](https://github.com/whereareiam/anvil/blob/dev/anvil-player/player-api/src/main/java/me/whereareiam/anvil/protocol/api/player/ProtocolPlayer.java)
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
with the engine. `ProtocolLibrary.create(PlayerRequest)` receives who logs in, with which client, and where to:

| Group | Content |
|---|---|
| `getName()`, `getLogin()` | The player name and its `PlayerLogin`: the authentication mode and either an offline username, an account ID, or a session identity. `getUsername()` returns the offline username, or the name when the login declares none |
| `getClientVersion()` | The exact Minecraft version the client speaks. Each Minecraft version appears in only one of your releases, so resolve the release from it through your own release data |
| `getConnection()` | `GameConnection`: the game listener's address, the optional virtual host the handshake announces, and the optional source address the socket binds to |

A leased login never reaches the library; Anvil replaces it with the leased account first. Your library
must honour the virtual host and source address, or refuse the player when it is created; it never ignores
either one, just as a library that cannot redirect its session service refuses a session identity.

Create a disconnected client; connection behavior is supplied through the
appropriate capability. `ProtocolPlayer.requestedProfile()` returns the profile the client requests, and
`grantedProfile()` returns, while it is connected, the profile the server or proxy granted in its login
success, from the moment that arrives: before the client enters the game, and while a proxy keeps the client
on the loading screen. The MCProtocol worker reports it as the `player.granted` event
(`PlayerConnectionEvent.GRANTED`) as soon as the login succeeds. Each process's Players capability finds the player by the UUIDs of its granted profiles, so the
Players capability of no server or proxy finds a player whose library never reports a granted profile.
Implement `grantedProfiles()` when the library keeps every profile granted at earlier logins; without
it, a player is found only under the current one. Destroying a player releases its client resources,
and `close()` attempts cleanup of all clients and owned workers even after a failure.

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

## Run worker extensions

Capabilities do their client-side work in worker extensions (`WorkerExtension`), which speak an edition's protocol
rather than a library's packets. A library that runs them gets every capability built on that protocol, the built-in
ones included, without code of its own for any of them. Declare what your players speak in
`ProtocolLibraryProvider.protocols()`, such as `Set.of(JavaProtocol.class)`. Composition skips a capability whose
`requiredProtocols()` your library lacks before any player exists, and requesting it names the missing protocol.

Two kits hold the runtime, so a library supplies only its connection and its packet translation:

| Artifact | Use |
| --- | --- |
| `player-worker` | `WorkerExtensions` discovers the extensions on the class path, keeps those that target your library or every library and accept your native session type, binds them to each player, buffers operations while binding, and reports each unavailable one with its reason. `NativeSessionBindings` runs `bindNativeSession(...)`, and the `bindConnection(...)` built on it, across your sessions. `WorkerProtocols` lists the protocols a player speaks. |
| `protocol-java-worker` | `JavaPacketDispatcher` is the player's `JavaProtocol` over the `JavaConnection` you implement: send a packet in your release's form, and say which packets your release carries. Hand every packet your player receives, already translated, to `deliver(...)`, and call `disconnected()` once its connection closes. Its `world()` is the player's `JavaWorld`, which the delivered packets keep up to date. |

A worker player implements `PlayerBindingContext<Object>` with them:

```java
JavaPacketDispatcher java = new JavaPacketDispatcher("example", connection);
WorkerProtocols protocols = WorkerProtocols.builder("example")
		.speak(JavaProtocol.class, java, java::scoped)
		.speak(JavaWorld.class, java.world(), track -> java.world())
		.build();
WorkerExtensions extensions = WorkerExtensions.discover("example", Object.class, Set.of());
WorkerExtensions.PlayerBindings bindings = extensions.bind(player, protocols);
```

The player's `protocol(Class)` returns `protocols.protocol(type)`, so extensions reach the world the same way as the
protocol, and `bindNativeSession(...)` registers with its
`NativeSessionBindings`, which it attaches to each session it opens and ends when that session's connection ends.
A host request names an operation: `bindings.operation(id)` returns it with its `OperationDescriptor`, whose request
and response types your transport decodes and encodes.

For the host's side, implement `ProtocolChannel` and expose it through `ProtocolPlayer.channel()`; capability
composition hands that same channel to `ProtocolPlayerCapabilityContext.channel()`. Its
`installedCapabilities()` and `unavailableCapabilities()` report `WorkerExtensions.capabilities()` and
`unavailable()`. Composition skips every capability your worker does not install, together with the ones that
depend on it, with the worker's reason; a worker-backed capability of a player without a channel is skipped with
the note that your players run no worker extensions. A library that runs extensions in the host's JVM calls the
operations directly; one with worker processes, as MCProtocolLib has, owns its own transport and codec.

The independent fixture library `test-java-library` in Anvil's test modules is a complete in-process example: it
speaks the Java Edition protocol to a server that echoes chat, and Anvil's runtime tests compose the built-in
capabilities on it.

A worker extension needing a packet the protocol does not model belongs in the protocol: add the packet to
`protocol-java-api` and to the area translations of every library. Code that is tied to one library still reaches
its release-specific implementation through `PlayerBindingContext.adapter(Class)`, which your worker answers with
the adapters its segments provide.

For another library-specific service, implement a stable interface and return it from
`ProtocolPlayer.findService(Class<T>)`. A provider can resolve that service through
`ProtocolPlayerCapabilityContext.requireService(...)`, and declares your library ID in
`supportedLibraries()`, since it works only with your library. Player observations use the shared `PlayerCapabilityContext.observation()`
contract. Providers that need only those observations and capability dependencies use
`PlayerCapabilityProvider` from `capability-api`; they do not need a protocol-specific context.
Process capabilities backed by agents use `AgentProcessCapabilityProvider` and its own-process `channel()`,
independently of protocol player composition.

Keep the interface meaningful for your library. For example, an adapter API can expose typed client
commands while retaining packet-library details in its implementation.

## Compose the public player facade

The installed `ProtocolPlayerComposer` wraps a library-owned `ProtocolPlayer` as a public
`SimulatedPlayer`. A custom composer implements this signature:

```java
@NotNull SimulatedPlayer compose(
		@NotNull ProtocolPlayer player,
		@NotNull PlayerConnection connection,
		@Nullable PresentationMetadata metadata,
		@NotNull Consumer<SimulatedPlayer> onDestroyed
);
```

`ProtocolPlayer` belongs to `me.whereareiam.anvil.protocol.api.player`. `SimulatedPlayer` belongs to
`me.whereareiam.anvil.api.player`, `PlayerConnection` to `me.whereareiam.anvil.api.model.player`; `PresentationMetadata` belongs to
`me.whereareiam.anvil.api.model`. The destruction callback uses `java.util.function.Consumer`,
with JetBrains nullability annotations on the signature.

The protocol player manager passes metadata from `PlayerOptions` alongside the native player and its
`PlayerConnection` (`me.whereareiam.anvil.api.model.player`), with the target process named also for the
entrypoint, which capabilities that follow the player's connection receive as
`PlayerCapabilityContext.connection()`. Preserve the optional metadata in `SimulatedPlayer.metadata()` while
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
the installed library with the strongest support for its version. Capabilities served by worker
extensions apply to your players through the protocols you declare; only a capability that uses your library's own
services declares your ID in `supportedLibraries()`.

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
