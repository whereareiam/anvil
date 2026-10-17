---
title: Protocol adapters
description: Connect a player's typed channel to a worker extension that acts over the Java Edition protocol.
---

Use a protocol adapter for behavior carried by the native player's connection. Keep your public
capability and request models in a feature API, depending on `me.whereareiam.anvil:api`. Put provider
and worker registration in the wiring artifact, which adds `me.whereareiam.anvil:capability-protocol-api`.

Host providers call `ProtocolPlayerCapabilityContext.channel()`, which returns the player's typed
`ProtocolChannel`. Worker extensions implement `WorkerExtension` from the player API,
`me.whereareiam.anvil:protocol-api`, which `capability-protocol-api` brings along. Its `bind` method receives a
`PlayerBindingContext` for the player's lifecycle and protocol, and an `OperationRegistry` for typed handlers.
The protocol library owns serialization and transport; your extension owns the operations, the per-player state,
and the listeners.

Packet classes differ between releases of a client library: MCProtocolLib renamed its packets and moved them
between packages from Minecraft 1.18.2 to 1.21.11. Your extension does not see them. It speaks the Java Edition
protocol, `me.whereareiam.anvil:protocol-java-api`, whose packets are the same for every release; the library
translates them for the release the player runs.

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

import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * Shared descriptors used by the host provider and native worker extension.
 */
public final class RoundTripOperations {
	/**
	 * Returns the request message from the worker.
	 */
	public static final OperationDescriptor<RoundTripRequest, String> ROUNDTRIP = new OperationDescriptor<>(
			"com.example.roundtrip.message.v1", RoundTripRequest.class, String.class
	);
}
```

The descriptor binds a namespaced operation ID to explicit request and response classes. Use a new
ID when changing its payload contract. MCProtocol requests use immutable object models, or `Void`
for an operation with no request. Keep packet-library types and transport annotations out of these
models; the bundled codec supports constructors whose parameter names are retained by `-parameters`.

## Bind behavior to a player

Put this registration class in your wiring artifact's `src/main/java/com/example/roundtrip/java`
directory. Its compile dependencies are your feature API and Anvil's `protocol-api` (and `protocol-java-api` once it
uses packets); it does not compile against a client library, because it runs on every release of every library.

```java
package com.example.roundtrip.java;

import com.example.roundtrip.RoundTripOperations;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
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
		return Optional.empty();
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
`Object.class` accepts every release.

`PlayerBindingContext` supplies the existing player's protocol, connection lifecycle, and event emission to this
binding. Keep the binding's state in the object returned from `bind`.
`OperationRegistry` accepts the shared descriptor and a typed handler; the worker encodes and decodes the
values outside the feature implementation.

Create `src/main/resources/META-INF/services/me.whereareiam.anvil.protocol.api.worker.WorkerExtension`
in the wiring artifact containing:

```text
com.example.roundtrip.java.RoundTripExtension
```

The host `ProtocolPlayerCapabilityProvider` needs its own [service descriptor](../../packaging/index.md).
Give its descriptor the matching capability ID `com.example.roundtrip` and return `Set.of("mcprotocol")`
from `supportedLibraries()`.
Obtain `ProtocolChannel channel = context.channel()` from `protocol.api.channel` during
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

## Act over the Java Edition protocol

`player.protocol(JavaProtocol.class)` returns the player's protocol. Packets are immutable values grouped by area,
such as `packet.chat` and `packet.interaction`, one class per game packet:

```java
JavaProtocol protocol = player.protocol(JavaProtocol.class);
Subscription chat = protocol.listen(ClientboundSystemChat.class, packet -> received(packet.getText()));
protocol.send(new ServerboundChatCommand("spawn"));
```

The protocol belongs to the player, not to one connection: a listener receives the packets of whichever connection
is current, never a late packet of a replaced one, and `send` uses the current connection and fails while the
player is disconnected. Close your subscriptions from `WorkerBinding.close()`, and keep state such as counters in an
object created by `bind`, so different players cannot share it.

A release may lack a packet; `protocol.supports(type)` tells. Listen to an optional packet only when the release
carries it. When your capability cannot work without a packet, throw `ProtocolUnavailableException` from `bind`:
the worker reports the capability as unavailable with your message, and the player keeps its other capabilities.
`listen` and `send` throw it themselves for a packet the release cannot carry. A player of a library that does not
speak the Java Edition protocol throws it from `protocol(JavaProtocol.class)`.

`player.bindNativeSession(...)` tells you when a connection starts: its callback runs before each connection logs
in and returns a `Subscription` that is closed when that connection ends or a new one replaces it. Use it for state
that belongs to one connection, such as cookies.

An `EventDescriptor<E>` declares the event ID and payload class shared by the worker and host. Send a value with
`player.emit(eventDescriptor, payload)` and receive it through `channel.subscribe(eventDescriptor, listener)`. A
connection event, for example, carries a `PlayerConnectionEvent` with the profile the joined process granted at
login, or the reason the connection ended. Use `Void` with a `null` payload when an event carries no value.

Register host subscription cleanup with `context.onDestroy(...)`. `player.viewRotation()` returns the shared
`ViewRotation`: yaw and pitch in degrees, including server corrections. Movement updates it when sending a new
direction, and interaction reads it for the current view; `player.viewRotation(rotation)` only records the value.

## Reach a packet the protocol does not carry yet

The Java Edition protocol carries the packets Anvil's own capabilities need. A packet it does not model yet belongs
in the protocol: add it to `protocol-java-api` and its translation to the library's client segments, so every
capability can use it. Until then, an external capability can keep its own release-specific code in segments.

For example, a family whose capability teleports a player declares the port
`com.example.teleport.packet.TeleportPackets<S>` in its API artifact: a stateless interface whose `sessionType()`
names the release's session class and whose methods take that session. Its library side, `teleport-mcprotocol`,
holds the extension and one segment per range of releases, such as `V1_18_2` and `V1_21_11`. A segment is named
after the release key it starts at and serves every later release up to the next segment, and implements only the
port. The extension obtains the selected segment's implementation, the adapter, while binding a player:

```java
TeleportPackets<?> packets = player.adapter(TeleportPackets.class);
```

Cast `player.nativeSession()` with `packets.sessionType()` before each call. Call `adapter` while binding, never in
the extension's constructor, and never call `ServiceLoader` yourself. When no segment serves the release, the
segment does not link, or more than one adapter is declared, `adapter` throws `ProtocolUnavailableException`.

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
payloads, duplicate registration, per-player isolation, cleanup, and releases that lack a packet. Exercise
reconnects to catch duplicate listeners and stale-connection observations. For game actions, assert the result through a
real server observation. See the repository's [live and worker tests](../../../contributing/testing/live/index.md).

For another client library, bind to its own session type or expose a stable library-specific
service. See [Protocol libraries](../../protocol-libraries/index.md).
