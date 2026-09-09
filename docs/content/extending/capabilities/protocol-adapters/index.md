---
title: Protocol adapters
description: Connect a typed capability channel to a worker extension using the selected native client SDK.
---

Use a protocol adapter for behavior carried by the native player's connection. Keep your public
capability and request models in a feature API, depending on `me.whereareiam.anvil:api`. Put provider
and worker registration in the wiring artifact, which adds `me.whereareiam.anvil:capability-protocol-api`.
The native implementation uses the actual backend SDK. For MCProtocol, that SDK context is
`org.geysermc.mcprotocollib.network.ClientSession`.

Host providers call `ProtocolPlayerCapabilityContext.channel()`. Worker extensions implement
`WorkerExtension<ClientSession>` from the protocol capability API. Its `bind` method receives a
`PlayerBindingContext<ClientSession>` for native SDK access and an `OperationRegistry` for typed
handlers. The runtime owns serialization and transport; your extension owns the operations, native
packets, and player-specific state.

## Define a shared channelOperation

This diagnostic channelOperation returns a supplied message without sending a game packet. It checks the
host/worker installation path. Put the request in your feature API and the channelOperation descriptor in
your wiring artifact, both under `src/main/java/com/example/roundtrip`. Enable Lombok and Java's
`-parameters` compiler option for immutable request construction.

`RoundTripRequest.java`:

```java
package com.example.roundtrip;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Message passed to the worker diagnostic channelOperation.
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

The descriptor binds a namespaced channelOperation ID to explicit request and response classes. Use a new
ID when changing its payload contract. MCProtocol requests use immutable object models, or `Void`
for an channelOperation with no request. Keep packet-library types and transport annotations out of these
models; the bundled codec supports constructors whose parameter names are retained by `-parameters`.

## Bind behavior to a player

Put this registration class in your wiring artifact's `src/main/java/com/example/roundtrip/mcprotocol`
directory. Its compile dependencies are your feature API, Anvil's `capability-protocol-api`, and the MCProtocol
SDK matching the native runtimes you support. For a real feature, delegate packet behavior to a
focused native implementation that depends on its feature API and the SDK.

```java
package com.example.roundtrip.mcprotocol;

import com.example.roundtrip.RoundTripOperations;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.jetbrains.annotations.NotNull;

/**
 * Registers a diagnostic channelOperation for each MCProtocol player.
 */
public final class RoundTripExtension implements WorkerExtension<ClientSession> {
	@Override
	public @NotNull String id() {
		return "com.example.roundtrip";
	}

	@Override
	public @NotNull String backendId() {
		return "mcprotocol";
	}

	@Override
	public @NotNull Class<ClientSession> backendType() {
		return ClientSession.class;
	}

	@Override
	public @NotNull WorkerBinding bind(
			@NotNull PlayerBindingContext<ClientSession> bindingContext,
			@NotNull OperationRegistry operations
	) {
		operations.register(RoundTripOperations.ROUNDTRIP, request -> request.getMessage());
		return () -> { };
	}
}
```

`PlayerBindingContext` supplies the existing player's SDK session, connection lifecycle, and event
emission to this binding. Keep the binding's state in the object returned from `bind`.
`OperationRegistry` accepts the shared descriptor and a typed handler; encoding and decoding stay
outside the feature implementation.

Create `src/main/resources/META-INF/services/me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension`
in the adapter artifact containing:

```text
com.example.roundtrip.mcprotocol.RoundTripExtension
```

The host `ProtocolPlayerCapabilityProvider` needs its own [service descriptor](../../packaging/index.md).
Give its descriptor the matching capability ID `com.example.roundtrip` and return `Set.of("mcprotocol")`
from `supportedProtocolIds()`.
Obtain `CapabilityChannel channel = context.channel()` from `capability.protocol.api.player.channel` during
its `create` method and check that
`channel.installedCapabilities()` contains the capability ID before constructing the host capability.

With that channel, this fragment performs the diagnostic request:

```java
import com.example.roundtrip.RoundTripOperations;
import com.example.roundtrip.RoundTripRequest;

String reply = channel.request(RoundTripOperations.ROUNDTRIP, new RoundTripRequest("hello"));
```

The reply should be `hello`. This proves the registered worker channelOperation ran; it does not establish
that a Minecraft server accepted a game action.

## Own native state and listeners

Within the binding, call `bindingContext.backend().send(...)` with actual MCProtocol packet values.
`backend()` requires a connected player and returns that connection generation's session. Resolve
it when sending rather than retaining it across a reconnect.

Attach native listeners through `bindingContext.bindBackend(...)`. Its callback receives each replacement
session before login starts and returns a `Subscription` that removes that session's listeners.
In native callbacks, check `bindingContext.isCurrentBackend(session)` before updating observations; a
callback already in flight can outlive listener removal. Close the returned registration from your
`WorkerBinding.close()`. Keep state such as inventory contents and action counters in an object
created by `bind`, so different players cannot share it.

An `EventDescriptor<E>` declares the event ID and payload class shared by the worker and host.
Send a value with `bindingContext.emit(eventDescriptor, payload)` and receive that value through
`channel.subscribe(eventDescriptor, listener)`. For example, a connection event carries a
`PlayerConnectionEvent` value; the descriptor identifies how to deliver and decode it. Use `Void`
with a `null` payload when an event carries no value.

Register host subscription cleanup with `context.onDestroy(...)`. `bindingContext.viewRotation()`
returns the shared `ViewRotation`: yaw and pitch in degrees, including server corrections. Movement
updates this state when sending a new direction, and interaction reads it for the current view.
Calling `bindingContext.viewRotation(rotation)` records the value; the native adapter remains
responsible for sending the packet.

## Declare support and verify it

Override `supports(int protocolNumber)` when your native bindings support a limited set of protocols.
The worker checks the backend ID and exact SDK context type before binding. Extensions excluded by
version support are absent from `installedCapabilities()`. Native extensions load in the selected
worker JVM. MCProtocol selects the protocol-library JAR from its pinned catalog and starts that
worker with the Java executable of the Anvil JVM.

Operation IDs must be unique and namespaced. The lifecycle IDs `create`, `destroy`, and `shutdown`
are reserved. Register operations only during `bind`; a capability cannot add handlers after its
player is exposed.

Test the packaged adapter at every supported catalog version. Cover requests, events, malformed
payloads, duplicate registration, per-player isolation, and cleanup. Exercise reconnects to catch
duplicate listeners and stale-session observations. For game actions, assert the result through a
real server observation. See the repository's [live and worker tests](../../../contributing/testing/live/index.md).

For another client library, bind to its actual SDK context or expose a stable backend-specific
service. See [Protocol providers](../../protocol-providers/index.md).
