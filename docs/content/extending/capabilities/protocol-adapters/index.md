---
title: Protocol adapters
description: Implement capability behavior through an explicit backend service and MCProtocol worker extension.
---

Use a protocol adapter for behavior carried by the native player's connection. The public capability
continues to depend on `anvil-api`; its implementation depends on the selected backend's execution
service API and packet library.

For the bundled `mcprotocol` backend, the extension surface is `protocol-adapter-api`. Host providers
use `ProtocolPlayerConnection`; worker adapters use `ProtocolWorkerPlayer`. Packet values on the
worker side are MCProtocolLib values. These contracts do not translate packets from unrelated
client libraries.

## Connect the host and worker halves

1. Implement `PlayerCapabilityProvider` in your capability artifact. Set its supported protocol ID
   to `mcprotocol` and declare actual predecessor capabilities.
2. Obtain `ProtocolPlayerConnection` through `context.requireService(...)` in `create`.
3. Implement `ProtocolCapabilityAdapter` with an `id()` matching the host provider's descriptor ID.
4. Register operations and inbound packet listeners during `install`.
5. Check `connection.workerCapabilities()` before creating a host implementation that requires the
   worker adapter. An adapter excluded by `supports(protocolNumber)` will not appear in that set.

The worker adapter's service file is
`META-INF/services/me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter`.
Its content is the fully qualified implementation class name. The host provider needs its own
[capability service descriptor](../../packaging/index.md).

## Register an operation

This scoped fragment belongs in `ProtocolCapabilityAdapter.install`, where `registry` is its
`ProtocolCapabilityAdapterRegistry` parameter. It registers a round-trip diagnostic operation
without sending a game packet:

```java
registry.operation("com.example.echo.roundtrip", (player, arguments) ->
		player.mapper().createObjectNode().put("message", arguments.path("message").asText()));
```

The corresponding host fragment assumes `connection` was resolved in the provider's `create` method:

```java
String reply = connection.request("com.example.echo.roundtrip",
		arguments -> arguments.put("message", "hello")).path("message").asText();
```

For packet behavior, use `registry.packets(...)` to inspect inbound packets and
`ProtocolWorkerPlayer.send(...)` to send the backend's packet types. Store per-player adapter state
with `player.state(State.class, State::new)` and emit namespaced host events with `player.emit(...)`.
`connection.subscribe(...)` observes those events for that host player. Packet listeners receive
packets for every worker player, so shared listener fields must not accidentally mix player state.

## Keep registration and version support explicit

Operation names must contain a namespace separator (`.` or `:`), and must be globally unique.
`create`, `destroy`, and `shutdown` are reserved lifecycle operations. Registration is accepted only
during the adapter's installation call. Choose stable names owned by your extension, such as
`com.example.combat.attack`.

Override `supports(int protocolNumber)` for packet bindings that only support specific protocols.
Returning `true` by default is appropriate only when the adapter actually supports every worker
runtime on which it can be installed. The selected native codec is resolved by the backend catalog;
adding a capability does not add a new codec or a new catalog version.

## Test the real boundary

Test host requests against the packaged worker adapter at every supported catalog version. Include
unsupported-version diagnostics, duplicate registration, packet observations, and player destruction.
For game actions, assert the result through a real server observation as well as the request result.
The repository's [live and worker tests](../../../contributing/testing/live/index.md) show the relevant
verification commands.

For another client library, expose a service owned by that backend's public adapter API and implement
the capability against it. See [Protocol providers](../../protocol-providers/index.md).
