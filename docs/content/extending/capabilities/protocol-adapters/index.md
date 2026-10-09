---
title: Protocol adapters
description: Connect a typed capability channel to a worker extension and to release-specific code for each library release.
---

Use a protocol adapter for behavior carried by the native player's connection. Keep your public
capability and request models in a feature API, depending on `me.whereareiam.anvil:api`. Put provider
and worker registration in the wiring artifact, which adds `me.whereareiam.anvil:capability-protocol-api`.

Host providers call `ProtocolPlayerCapabilityContext.channel()`. Worker extensions implement
`WorkerExtension` from the protocol capability API. Its `bind` method receives a
`PlayerBindingContext` for the player's native session and lifecycle, and an `OperationRegistry` for
typed handlers. The runtime owns serialization and transport; your extension owns the operations,
the per-player state, and the listeners.

Packet classes differ between releases of a client library. MCProtocolLib, for example, renamed its
packets and moved them between packages from Minecraft 1.18.2 to 1.21.11, and its session type
changed with them. Code that uses those classes therefore lives in segments, one per range of
releases, behind a library-neutral port that your extension obtains for the player's release.

## Define a shared operation

This diagnostic operation returns a supplied message without sending a game packet. It checks the
host/worker installation path. Put the request in your feature API and the operation descriptor in
your wiring artifact, both under `src/main/java/com/example/roundtrip`. Enable Lombok and Java's
`-parameters` compiler option for immutable request construction.

`RoundTripRequest.java`:

```java
package com.example.roundtrip;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Message passed to the worker diagnostic operation.
 */
@Value
public class RoundTripRequest {
	@NotNull String message;
}
```

`RoundTripOperations.java`:

```java
package com.example.roundtrip;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;

/**
 * Shared descriptors used by the host provider and native worker extension.
 */
public final class RoundTripOperations {
	/**
	 * Returns the request message from the worker.
	 */
	public static final ChannelOperation<RoundTripRequest, String> ROUNDTRIP = new ChannelOperation<>(
			"com.example.roundtrip.message.v1", RoundTripRequest.class, String.class
	);
}
```

The descriptor binds a namespaced operation ID to explicit request and response classes. Use a new
ID when changing its payload contract. MCProtocol requests use immutable object models, or `Void`
for an operation with no request. Keep packet-library types and transport annotations out of these
models; the bundled codec supports constructors whose parameter names are retained by `-parameters`.

## Bind behavior to a player

Put this registration class in your wiring artifact's `src/main/java/com/example/roundtrip/mcprotocol`
directory. Its compile dependencies are your feature API and Anvil's `capability-protocol-api`; it does
not compile against MCProtocolLib, because it runs on every release.

```java
package com.example.roundtrip.mcprotocol;

import com.example.roundtrip.RoundTripOperations;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Registers a diagnostic operation for each MCProtocol player.
 */
public final class RoundTripExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return "com.example.roundtrip";
	}

	@Override
	public @NotNull Optional<String> libraryId() {
		return Optional.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull OperationRegistry operations
	) {
		operations.register(RoundTripOperations.ROUNDTRIP, request -> request.getMessage());
		return () -> { };
	}
}
```

`libraryId()` names the library whose workers install the extension; an empty value installs it in
every library's worker. A worker also requires `nativeSessionType()` to accept its own session type;
`Object.class` accepts every release, and a segment's port casts the session to the release's type.

`PlayerBindingContext` supplies the existing player's native session, connection lifecycle, and event
emission to this binding. Keep the binding's state in the object returned from `bind`.
`OperationRegistry` accepts the shared descriptor and a typed handler; encoding and decoding stay
outside the feature implementation.

Create `src/main/resources/META-INF/services/me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension`
in the wiring artifact containing:

```text
com.example.roundtrip.mcprotocol.RoundTripExtension
```

The host `ProtocolPlayerCapabilityProvider` needs its own [service descriptor](../../packaging/index.md).
Give its descriptor the matching capability ID `com.example.roundtrip` and return `Set.of("mcprotocol")`
from `supportedLibraries()`.
Obtain `CapabilityChannel channel = context.channel()` from `capability.protocol.api.player.channel` during
its `create` method and construct the host capability over it. Do not check
`channel.installedCapabilities()` yourself: composition creates the provider only for players whose worker
installed `com.example.roundtrip`. For any other player it skips the provider and every provider that
depends on its capability, and requesting the capability throws `CapabilityUnavailableException` with the
worker's reason, such as a member missing from the loaded release, or `not installed by the mcprotocol worker`.

With that channel, this fragment performs the diagnostic request:

```java
import com.example.roundtrip.RoundTripOperations;
import com.example.roundtrip.RoundTripRequest;

String reply = channel.request(RoundTripOperations.ROUNDTRIP, new RoundTripRequest("hello"));
```

The reply should be `hello`. This proves the registered worker operation ran; it does not establish
that a Minecraft server accepted a game action.

## Own native state and listeners

Within the binding, `player.nativeSession()` returns the current connection generation's native
session and requires a connected player. Resolve it when sending rather than retaining it across a
reconnect, and pass it to your port, which sends the release's packet values.

Attach native listeners through `player.bindNativeSession(...)`. Its callback receives each replacement
session before login starts and returns a `Subscription` that removes that session's listeners.
In native callbacks, check `player.isCurrentNativeSession(session)` before updating observations; a
callback already in flight can outlive listener removal. Close the returned registration from your
`WorkerBinding.close()`. Keep state such as inventory contents and action counters in an object
created by `bind`, so different players cannot share it.

An `EventDescriptor<E>` declares the event ID and payload class shared by the worker and host.
Send a value with `player.emit(eventDescriptor, payload)` and receive that value through
`channel.subscribe(eventDescriptor, listener)`. For example, a connection event carries a
`PlayerConnectionEvent` value; the descriptor identifies how to deliver and decode it. Use `Void`
with a `null` payload when an event carries no value.

Register host subscription cleanup with `context.onDestroy(...)`. `player.viewRotation()`
returns the shared `ViewRotation`: yaw and pitch in degrees, including server corrections. Movement
updates this state when sending a new direction, and interaction reads it for the current view.
Calling `player.viewRotation(rotation)` records the value; the port remains responsible for sending
the packet.

## Put release-specific code in segments

Anvil's own packet capabilities show the layout. The movement family declares the port
`me.whereareiam.anvil.capability.movement.packet.MovementPackets<S>` in its API artifact: a stateless
interface whose `sessionType()` names the release's session class and whose methods take that session.
Its library side, `movement-mcprotocol`, holds the extension and one segment per range of releases:
`V1_18_2` and `V1_21_11`. A segment is named after the release key it starts at
and serves every later release up to the next segment. Each implements only the port:

```java
public final class McProtocolMovementPackets implements MovementPackets<Session> {
	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void move(@NotNull Session session, @NotNull Position position) {
		session.send(new ServerboundMovePlayerPosRotPacket(position.isOnGround(), false,
				position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch()));
	}
}
```

That is the `V1_21_11` segment, which imports `org.geysermc.mcprotocollib.network.Session`; the
`V1_18_2` segment sends its release's `ServerboundMovePlayerPosRotPacket` through
`com.github.steveice10.packetlib.Session` instead. The extension obtains the selected segment's
implementation, the adapter, while binding a player, and hands it to the library-neutral binding:

```java
MovementPackets<?> packets = player.adapter(MovementPackets.class);
return new MovementBinding<>(packets).bind(player, operations);
```

The binding casts `player.nativeSession()` with `packets.sessionType()` before each call. Call
`adapter` while binding, never in the extension's constructor: a worker creates every discovered
extension before it knows which ones it installs. Do not call `ServiceLoader` yourself.

The worker selects one segment per side for its release and checks the segment's recorded linkage
against the loaded release before handing out its adapter. When no segment serves the release, the
segment does not link, or more than one adapter is declared, `adapter` throws
`AdapterUnavailableException` and the worker reports your capability as unavailable with that reason,
while the player keeps its other capabilities.

## Package a segment

A segment JAR contains the port implementation, its one-line `META-INF/services/<port>` descriptor,
and two resources the worker reads:

| Resource | Content |
|---|---|
| `META-INF/anvil/segment.properties` | `library` (the library ID, such as `mcprotocol`), `owner` (the side, such as `roundtrip-mcprotocol`), and `since` (the release key the segment starts at, such as `1.16.5`) |
| `META-INF/anvil/segment/linkage.txt` | One line per class, field, or method of the library that the segment uses, which the worker verifies against the loaded release |

For a release, the worker keeps the segment of each owner with the greatest `since` that does not
exceed the release key, and removes the owner's other segments from its class path. A linkage line
names a class (`class a.b.C`), a field (`field a.b.C name:Ldescriptor;`), or a method
(`method a.b.C name(parameters)return`) with binary names and JVM descriptors, followed by optional
requirements such as `public`, `static` or `instance`, and `class` or `interface`. The worker reads
the file even when it is empty, and an empty file verifies nothing.

Anvil's own families apply internal build conventions that compile each segment against the release
named by its folder, generate both resources, and fail the build when a segment does not link against
a later release it serves. Those conventions are not published, so an external build writes the
resources itself. Compile each segment against its release's MCProtocolLib module with `compileOnly`
and never bundle the library: a worker refuses a class path entry outside the release's own runtime
that contains netty, MCProtocolLib, CloudburstMC, or adventure classes. Install the segment JARs with
your wiring artifact on the Anvil runtime class path.

## Declare support and verify it

The worker installs an extension whose `libraryId()` names its library, or is empty, and whose
`nativeSessionType()` accepts the worker's session type; other extensions are absent from
`installedCapabilities()`. Native extensions load in the selected worker JVM. MCProtocol starts that
worker with the Java executable of the Anvil JVM on the selected release's pinned runtime closure,
followed by the host class path with one segment per side.

Operation IDs must be unique and namespaced. The lifecycle IDs `create`, `destroy`, and `shutdown`
are reserved. Register operations only during `bind`; a capability cannot add handlers after its
player is exposed.

Test the packaged adapter on every library release you support. Cover requests, events, malformed
payloads, duplicate registration, per-player isolation, and cleanup. Exercise reconnects to catch
duplicate listeners and stale-session observations. For game actions, assert the result through a
real server observation. See the repository's [live and worker tests](../../../contributing/testing/live/index.md).

For another client library, bind to its own session type or expose a stable library-specific
service. See [Protocol libraries](../../protocol-libraries/index.md).
