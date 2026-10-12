---
title: Authentication
description: Use repeatable offline players or an explicitly configured private online account for local tests.
---

Offline players are the default for automated plugin tests. Use an online account only when a local
journey specifically depends on authenticated Minecraft identity.
Keep real account sign-in out of CI.

## Add a local account

An Anvil account is one Microsoft account. You sign it in once, without choosing a
[protocol library](../../../getting-started/installation/index.md), and every installed library that plays
online derives its own session from it when one of its players uses the account: MCProtocolLib (`mcprotocol`)
a Java Edition session, CloudburstMC Protocol (`cloudburst`) a Bedrock Edition session.

Use the Anvil IntelliJ panel's **Accounts** action to sign in, or, without IntelliJ IDEA, the Gradle task of
the project that applies the Anvil plugin:

```shell
./gradlew anvilLogin --account=main
```

Follow the device-code instructions in your browser. After the sign-in, each installed library checks what it
can do with the account, and the task prints one line per library:

```text
Signed in account 'main'.
mcprotocol: available as Steve (069a79f4-44e9-4726-a5be-fca90e38aaf5)
cloudburst: unavailable, Account 'main' was refused by Minecraft's multiplayer service (HTTP 401), ...
```

A Microsoft account can own Java Edition and not Bedrock Edition, or the other way round. The account file
records each library's result: available with the profile its players sign in as, unavailable with the
library's reason, or unknown when the library could not tell, for example because its service was unreachable
or the library was installed after the sign-in. Each library checks again whenever one of its players uses
the account and records the new result. To check an account again after buying the other edition, sign it in
again under the same ID.

The task stores the account in the directory configured by `anvil { engine { accountsDirectory } }`, which
defaults to `~/.anvil/accounts`, as one JSON file named after the account ID, restricted to the owner on
filesystems supporting POSIX permissions. Remove an account with `./gradlew anvilLogout --account=main`. You
can also import an account file signed in elsewhere. Account files written by Anvil 1.0, which belonged to
MCProtocolLib, keep working without a new sign-in; MCProtocolLib keeps its result for them, and the other
libraries check them the first time one of their players uses them. Sign in on developer machines only; the
stored file holds refresh tokens.

When a scenario references an account that is not stored, the run fails with the account ID and the
directory that was searched.

The global account directory defaults to `~/.anvil/accounts`. Configure another directory in
**Settings → Tools → Anvil → Accounts**. Project-specific accounts and pools are managed from the
**Accounts** action in the Anvil tool window; each project's directory defaults to a folder under
`~/.anvil/projects`, outside the project tree, so credentials are not committed with the source.

## Create and connect an online player

Configure `.onlineMode(true)` on the direct server or entry proxy being tested. Keep forwarded
backends configured through Anvil's [forwarding negotiation](../../environments/platforms/proxies/forwarding/index.md).

Place this helper at `src/anvil/java/com/example/test/AuthenticatedPlayers.java`. It assumes the scenario
entrypoint accepts online authentication and the Session capability is installed:

```java
package com.example.test;

import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.capability.session.Session;

public final class AuthenticatedPlayers {
	public static SimulatedPlayer connect(ScenarioContext anvil) {
		var player = anvil.players().create(PlayerOptions.builder()
				.name("OnlinePlayer")
				.login(PlayerLogin.account(AuthenticationMode.ONLINE, "main"))
				.build());
		var session = player.capability(Session.class);
		session.connect();
		session.connected();
		return player;
	}
}
```

`PlayerLogin` groups how a player logs in, and each factory creates one valid combination:

| Factory | Login |
|---|---|
| `PlayerLogin.offline()` | Offline, as the player's name; the default |
| `PlayerLogin.offline(username)` | Offline, as another username that several players may share |
| `PlayerLogin.account(mode, accountId)` | A stored account; its profile supplies the username |
| `PlayerLogin.session(mode, identity)` | An identity verified by a session server the scenario chose |
| `PlayerLogin.leased(mode)` | The account of a lease passed to `create(options, lease)` |

The account modes are `ONLINE` and `ON_REQUEST`; the account factories refuse `OFFLINE`, and every factory
refuses a blank value. A login therefore never carries a username next to an account, or an account ID next
to a session identity.

The protocol library selected for the player must not have recorded the account as unavailable, and that
library's release for the player's Minecraft version must support online authentication; otherwise creation
fails before the player connects, naming the account and giving the library's reason. When the library has
not checked the account yet, it checks it while the player is created and refuses the player with its reason
if it cannot use the account.

Call `AuthenticatedPlayers.connect(anvil)` from a local test in the same package and run that class with
`./gradlew anvilTest --tests 'your.package.YourOnlineTest'`. A connected session establishes that
the account authenticated at the target. Check [identity and server observations](../capabilities/server/index.md)
for assertions about what the platform sees; the authenticated identity is controlled by the account.

## Sign in a Bedrock player with Xbox

A Bedrock Edition player signs in with a stored account whose `cloudburst` result is available. Any account
signed in with `anvilLogin` or the IntelliJ **Accounts** action can be one; the sign-in prints whether it is.

Then turn on Xbox authentication on the Lumi server with `.onlineMode(true)`, which sets Lumi's
`network.xbox-auth`, and create the player with the account:

```java
var player = anvil.players().create(PlayerOptions.builder()
		.name("BedrockPlayer")
		.login(PlayerLogin.account(AuthenticationMode.ONLINE, "main"))
		.build());
```

The player logs in with the Xbox identity of the account, so Lumi grants and observes the same UUID and
username, unlike an offline Bedrock player. While Xbox authentication is on, Lumi refuses offline Bedrock
players. A Bedrock login is signed with the account's Xbox credentials, so `PlayerLogin.session(...)` is
refused for a Bedrock player, and Lumi refuses `sessionServer`. As for Java accounts, the worker receives
the tokens only on its private standard input.

## Authenticate on an offline-mode entrypoint

Some plugins keep the proxy or server in offline mode and require online authentication themselves for
selected connections, such as login plugins that recognize premium usernames. `AuthenticationMode.ONLINE`
refuses an offline-mode entrypoint. Use `AuthenticationMode.ON_REQUEST` for such a player: it signs in with
its account and authenticates when the entrypoint asks for it.

```java
var player = anvil.players().create(PlayerOptions.builder()
		.name("OnlinePlayer")
		.login(PlayerLogin.account(AuthenticationMode.ON_REQUEST, "main"))
		.build());
```

A connection that the entrypoint does not challenge joins unauthenticated, so assert what the plugin
and the platform report rather than relying on the mode.

For several real accounts, create a pool from local account IDs and create each player from the pool. The
player selects its protocol library as usual and leases the first free account that library recorded as
available, so a Java Edition player never receives a Bedrock-only account. A leased or directly selected
account is exclusive across every scenario of the engine until its player is destroyed or the lease is closed:

```java
var testers = anvil.accounts().pool(java.util.List.of("alice", "bob", "charlie"));
var login = PlayerLogin.leased(AuthenticationMode.ONLINE);
var one = anvil.players().create(PlayerOptions.builder().name("player-1").login(login).build(), testers);
var two = anvil.players().create(PlayerOptions.builder().name("player-2").login(login).build(), testers);
// The two players use different accounts their library can use. Player cleanup releases the leases.
```

When no free account of the pool is available to the player's library, creation fails before anything
connects, listing each account and why it was skipped: unavailable with the library's reason, unknown, or in
use. An account whose result is unknown is never lent; sign it in again, or let a player name it directly so
the library checks it.

To pick the account yourself, lease it and pass the lease. `testers.lease("mcprotocol")` leases for one
library; `testers.lease()` leases the next free account whatever the libraries recorded, and the player is
refused before it connects when its library recorded that account as unavailable:

```java
var premium = anvil.players().create(PlayerOptions.builder()
		.name("premium")
		.login(PlayerLogin.leased(AuthenticationMode.ON_REQUEST))
		.build(), testers.lease("mcprotocol"));
```

`create(name, lease)` signs in with `ONLINE`. A leased login is refused without a lease or pool, and a lease
is refused, without being claimed, for any other login. A leased account does not change the player's
protocol library. Creation fails before the player connects when that library cannot serve the player's
Minecraft version, and the lease returns to the pool.

A named pool keeps account IDs out of scenario code, so each machine can supply its own accounts.
Declare it in `pools.properties` in the account directory. The IntelliJ account manager writes this
file; on other machines, create it by hand:

```properties
schemaVersion=1
pool.testers=alice,bob,charlie
```

Each `pool.<name>` entry lists distinct account IDs separated by commas; leases follow that order.
Resolve the pool by name with `anvil.accounts().pool("testers")`. An undeclared pool, a repeated
account ID, or another `schemaVersion` fails the lookup.

## Sign in through another session server

An online-mode login involves a session server twice: the client reports the login to it, and the proxy or
server asks it to verify the login. By default that server is Mojang's and the player signs in with a stored
account. Both sides can name another server that speaks the same protocol, Yggdrasil, such as a self-hosted
one. The contract is its base address with two endpoints: `POST <base>/join` from the client and
`GET <base>/hasJoined?username=...&serverId=...` from the process.

Declare the address on the process that verifies logins, and give the player an identity that server accepts:

```java
URI sessionServer = URI.create("http://127.0.0.1:25580/session/minecraft");

MinecraftProxy proxy = MinecraftProxy.builder()
		.name("proxy")
		.platform(Platforms.VELOCITY)
		.distribution(Distribution.remote("3.5.1", "615"))
		.onlineMode(true)
		.sessionServer(sessionServer)
		.server("server")
		.build();
```

```java
var player = anvil.players().create(PlayerOptions.builder()
		.name("Alice")
		.login(PlayerLogin.session(AuthenticationMode.ONLINE, SessionIdentity.builder()
				.username("Alice")
				.uniqueId(aliceId)
				.accessToken(aliceToken)
				.sessionServer(sessionServer)
				.build()))
		.build());
```

A session identity replaces the account ID and works with `ONLINE` and `ON_REQUEST`. Anvil refuses the
player before it connects unless the process it joins declares the same session server, because any other
server would not verify it. Only Velocity can be redirected today: planning refuses `sessionServer` on any
other platform, and BungeeCord has no setting for it.

### Test without a real account

The optional `yggdrasil-mock` artifact is a test-only session server, so online-mode behavior can be tested
without a real account, and in CI:

```kotlin
dependencies {
	add("anvilImplementation", anvil.module("yggdrasil-mock"))
}
```

Start it before declaring the scenario, pass `yggdrasil.sessionServer()` to the process, and register the
accounts that exist. Registration returns the identity a player signs in with:

```java
YggdrasilMock yggdrasil = YggdrasilMock.start();
SessionIdentity alice = yggdrasil.register("Alice");
```

A username that is not registered fails verification, as an unpaid account does. The mock also answers the
profile lookup that login plugins use to recognize paid usernames, at `yggdrasil.profileLookup()` followed by
the username: status 200 for a registered username and 404 otherwise. Usernames match whatever their letter
case, as at Mojang.

A name change keeps the account's UUID and token. `rename` returns the identity that signs in under the new
name; from then on the lookup and login verification answer to the new name only, and the old one is free:

```java
SessionIdentity alicia = yggdrasil.rename(alice.getUniqueId(), "Alicia");
```

Renaming to a name another account holds fails; a change of letter case alone is allowed.

`yggdrasil.available(false)` makes every request fail with status 503, as during an outage. Close the mock when the tests that use it are done.

- The mock runs in the JVM that runs the tests and listens on loopback. A process in a container reaches it
  only when you start it with `YggdrasilMock.start(address)` on an address the container can route to.
- It signs nothing and serves no skins, so behavior that depends on signed profile properties is not covered.

## Keep the account store private

Treat the authentication directory as credentials. Exclude it from source control, diagnostic uploads,
and shared cache archives. Access and refresh tokens do not belong in Gradle inputs, CLI arguments,
environment variables, system properties, or scenario files. Each library sends the session it derived to
its worker through private stdin. Agent session credentials are separate per-run values.
