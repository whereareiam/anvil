---
title: Management
description: Create players with the intended target and identity, then manage their registration and lifetime.
---

Use `anvil.players().create("Alice")` to create an offline player with the scenario's default
entrypoint and a compatible native client version. This allocates the player without connecting it.

## Create with the default target

The examples use runtime access named `anvil`: a `ScenarioContext` in a test or application, or
`ScenarioAccess` inside a setup hook. Create a player whose name is not already registered:

```java
var alice = anvil.players().create("Alice");
```

The player uses the scenario's entrypoint, which names a direct server or proxy. Anvil resolves its
actual endpoint, so the caller does not need to discover the allocated port. Creation registers the
player but does not connect it. Use [Session](../capabilities/session/index.md) for login,
disconnection, reconnects, and kick observations.

## Override the connection target

For a scenario declaring a process named `proxy`, import
`me.whereareiam.anvil.api.model.player.PlayerOptions` and use:

```java
var alice = anvil.players().create(PlayerOptions.builder()
		.name("Alice")
		.connectTo("proxy")
		.build());
```

You must still connect through `Session`. `connectTo` selects an initial endpoint; it does not request
a backend transfer after login. For that, exercise your proxy plugin's routing behavior and observe
the resulting [server route](../capabilities/server/index.md).

## Select a native version

Anvil selects a supported native client compatible with every server reachable from the target.
You can request a specific version with `PlayerOptions.clientVersion(...)`; that version must also
be supported and compatible. Conflicting reachable server versions fail validation, and no protocol
translation plugin is inserted automatically.

Use `alice.clientVersion()` to inspect the resolved version. Consult
[versions and compatibility](../../environments/platforms/versions/index.md) when choosing
a distribution or diagnosing a mismatch.

## Use an online account deliberately

Offline authentication is the default. An online player requires a configured private authentication
profile and a compatible online-mode topology. Follow [authentication](../authentication/index.md)
for profile creation and `PlayerOptions` setup. Keep real account credentials out of test declarations
and automated CI suites.

## Release and replace a player

Destroy a player when you no longer need its resources or want to reuse its name. This independent
fragment assumes an open `ScenarioContext anvil` with no player named Alice yet:

```java
var alice = anvil.players().create("Alice");
alice.destroy();

var replacement = anvil.players().create("Alice");
```

`destroy()` unregisters the name and permanently releases the old player. Repeating destruction has
no additional effect. Use the replacement object for later actions and retrieve its capabilities
again; the old player and its capabilities are not reusable.

Disconnecting through Session alone does not unregister the name. Use Session's disconnect and wait
operations when the journey needs to observe a departure before releasing the player.

## Manage the registry and remaining players

| Method | Purpose |
|---|---|
| `anvil.players().get(name)` | Retrieve a player by its exact registered name; throws `NoSuchElementException` if absent |
| `anvil.players().all()` | Obtain an immutable snapshot of the registered players |
| `anvil.players().destroyAll()` | Release every currently registered player |

`all()` snapshots registry membership; the returned player objects still represent live players,
not frozen copies of their state. `destroyAll()` clears the current players and leaves the manager
available for creating others in the same scenario.

The scenario releases remaining players on teardown. Explicit destruction is useful for journeys
that create many clients over time or need to replace one without ending the environment. Keep
player and capability use inside that scenario's lifetime; a later execution creates its own objects.
