---
title: Management
description: Create players with the intended target, connection and login, then manage their registration and lifetime.
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

## Add an optional display label

Attach presentation details through `PlayerOptions` when a participant's role is more useful in
tooling than its connection name. With no player named `ReturningPlayer` registered yet, import
`me.whereareiam.anvil.api.model.PresentationMetadata` and
`me.whereareiam.anvil.api.model.player.PlayerOptions`, then create:

```java
var returning = anvil.players().create(PlayerOptions.builder()
		.name("ReturningPlayer")
		.metadata(PresentationMetadata.builder()
				.displayName("Returning player")
				.description("Participant used for repeat-login checks.")
				.build())
		.build());
```

The connection and registry name remains `ReturningPlayer`; retrieve it with
`anvil.players().get("ReturningPlayer")`. Creation still leaves it disconnected.
`returning.metadata()` exposes the optional details, or returns `null` when none were supplied.
Omitting metadata keeps the ordinary `create(name)` workflow unchanged.

## Override the connection target

`PlayerOptions` groups how a player logs in in its `PlayerLogin` and where it connects in its
`PlayerConnection`; both live in `me.whereareiam.anvil.api.model.player`. For a scenario declaring a process named `proxy`,
import `PlayerOptions` and `PlayerConnection` and use:

```java
var alice = anvil.players().create(PlayerOptions.builder()
		.name("Alice")
		.connection(PlayerConnection.to("proxy"))
		.build());
```

You must still connect through `Session`. The connection's `target` selects an initial endpoint; it does
not request a backend transfer after login. For that, exercise your proxy plugin's routing behavior and
observe the resulting [server route](../capabilities/server/index.md).

## Announce a virtual host

A client writes the server address it was told to join into its handshake. Proxies read that address to
route by host, for example through Velocity's `forced-hosts`. Set `virtualHost` to test such routing: the
player still connects to the target's real address and port, and only the announced host changes.

```java
var routed = anvil.players().create(PlayerOptions.builder()
		.name("Routed")
		.connection(PlayerConnection.builder()
				.target("proxy")
				.virtualHost("games.example.test")
				.build())
		.build());
```

With `forced-hosts."games.example.test" = ["game"]` in the proxy's settings, `routed` joins `game`
instead of the proxy's first `try` server. Without `virtualHost`, the handshake announces the target's
real host, as before. The host must not be blank, must not contain whitespace, and is at most 255
characters long.

## Connect from another loopback address

`sourceAddress` binds the player's socket to a local address before it connects, so the joined process
sees the player arrive from that address. Use it to test per-address behavior, such as connection
limits or address bans, with several players on one machine:

```java
var second = anvil.players().create(PlayerOptions.builder()
		.name("SecondAddress")
		.connection(PlayerConnection.builder().sourceAddress("127.0.0.2").build())
		.build());
```

Without `sourceAddress`, the system chooses the address, which is `127.0.0.1` for a loopback listener.
Anvil refuses the player when it is created, before connecting, and never falls back to `127.0.0.1`
when the address cannot be used:

| Limit | Why it is refused |
|---|---|
| A host name or a non-loopback address | A source address is an IP literal on the loopback interface, such as `127.0.0.2` or `::1` |
| A target that does not listen on loopback, or listens on another address family | A loopback source only reaches a loopback listener of the same family |
| An address this machine cannot bind | Linux routes all of `127.0.0.0/8` to loopback, so `127.0.0.2` and later addresses work without setup. macOS configures only `127.0.0.1`; add an alias first, for example `sudo ifconfig lo0 alias 127.0.0.2` |
| Docker execution | Docker forwards published ports through its own network, so the container sees the bridge gateway instead of the source address. Use local execution |

A proxy sees the source address; a backend behind it sees the proxy unless the proxy forwards player
information to it.

## Connect the same username twice

A player's name identifies it within the scenario and must be unique. It is also the Minecraft username,
unless its login declares another one with `PlayerLogin.offline(username)`. Give two players the same
username to test what your plugin does when an account that is already online joins again, or joins
through another proxy:

```java
var alice = anvil.players().create("Alice");
var again = anvil.players().create(PlayerOptions.builder()
		.name("alice-again")
		.login(PlayerLogin.offline("Alice"))
		.connection(PlayerConnection.to("secondary"))
		.build());
```

Both log in as `Alice` with the same offline UUID, and each keeps its own session, messages, and
[server route](../capabilities/server/index.md). Retrieve the second one with
`anvil.players().get("alice-again")`.

A username belongs to offline logins only. A player that signs in with an account takes the account's
username, so `PlayerLogin` offers no username next to an account; give such a player any unique name.
A server or proxy still decides what happens when a username joins while it is already online there:
Anvil only makes the second connection possible.

## Select a native version

Anvil selects a supported native client compatible with every server reachable from the target.
You can request a specific version with `PlayerOptions.clientVersion(...)`; that version must also
be supported and compatible. Conflicting reachable server versions fail validation, and no protocol
translation plugin is inserted automatically.

Use `alice.clientVersion()` to inspect the resolved version. Consult
[versions and compatibility](../../environments/platforms/versions/index.md) when choosing
a distribution or diagnosing a mismatch.

The player speaks that version through one protocol library. `PlayerOptions.protocolLibrary(...)`
selects it for one player, overriding `AnvilScenario.protocolLibrary(...)` and the engine's
`protocolLibrary`. Without a declaration, Anvil uses the installed library with the strongest support
for the version and refuses a tie.

## Use an online account deliberately

Offline authentication is the default. An online player requires a configured private authentication
account and a compatible online-mode topology. Follow [authentication](../authentication/index.md)
for account creation and the `PlayerLogin` factories. Keep real account credentials out of test declarations
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
