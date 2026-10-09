---
title: Authentication
description: Use repeatable offline players or an explicitly configured private online account for local tests.
---

Offline players are the default for automated plugin tests. Use an online account only when a local
journey specifically depends on authenticated Minecraft identity.
Keep real account sign-in out of CI.

## Add a local account

Use the [installed MCProtocolLib library](../../../getting-started/installation/index.md), whose ID is
`mcprotocol`. Accounts are signed in through one library: the engine's
`anvil { engine { protocolLibrary("mcprotocol") } }`, or the sole installed library that offers
authentication.
Use the Anvil IntelliJ panel's **Accounts** action to authenticate. Follow the library's device-code
instructions in your browser; Anvil retrieves the Minecraft username and UUID and stores the account
file under the configured account directory. You can also import an account file that the library
generated elsewhere. MCProtocolLib's library stores one account per JSON file and restricts files to
the owner on filesystems supporting POSIX permissions.

Without IntelliJ IDEA, sign in from the project that applies the Anvil plugin:

```shell
./gradlew anvilAccount --login=main
```

The task runs the same library workflow in a separate JVM and prints the device-code instructions.
It stores the account in the directory configured by `anvil { engine { accountsDirectory } }`, which
defaults to `~/.anvil/accounts`. Remove an account with `./gradlew anvilAccount --logout=main`. Sign in
on developer machines only; the stored file holds refresh tokens.

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

import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.capability.session.Session;

public final class AuthenticatedPlayers {
	public static SimulatedPlayer connect(ScenarioContext anvil) {
		var player = anvil.players().create(PlayerOptions.builder()
				.name("OnlinePlayer")
				.authentication(AuthenticationMode.ONLINE)
				.accountId("main")
				.build());
		var session = player.capability(Session.class);
		session.connect();
		session.connected();
		return player;
	}
}
```

The account must be stored by the protocol library selected for the player, and that library's release
for the player's Minecraft version must support online authentication; otherwise creation fails before
the player connects.

Call `AuthenticatedPlayers.connect(anvil)` from a local test in the same package and run that class with
`./gradlew anvilTest --tests 'your.package.YourOnlineTest'`. A connected session establishes that
the account authenticated at the target. Check [identity and server observations](../capabilities/server/index.md)
for assertions about what the platform sees; the authenticated identity is controlled by the account.

For several real accounts, create a pool from local account IDs. A leased or directly selected account
is exclusive across every scenario of the engine until its player is destroyed or the lease is closed:

```java
var testers = anvil.accounts().pool(java.util.List.of("alice", "bob", "charlie"));
var first = testers.lease();
var second = testers.lease();
var one = anvil.players().create("player-1", first);
var two = anvil.players().create("player-2", second);
// The two players use different authenticated accounts. Player cleanup releases the leases.
```

Account IDs belong to the protocol library that stores them. A pool holds one account per ID and refuses
an ID that several libraries store, so a lease always names the account its player signs in with. A player
created from a lease uses that account's library, whatever `protocolLibrary` the scenario or engine selects.
Creation fails before the player connects when that library cannot serve the player's Minecraft version, and
the lease returns to the pool.

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

## Keep the account store private

Treat the authentication directory as credentials. Exclude it from source control, diagnostic uploads,
and shared cache archives. Access and refresh tokens do not belong in Gradle inputs, CLI arguments,
environment variables, system properties, or scenario files. MCProtocol sends the access token to its
worker through private stdin. Agent session credentials are separate per-run values.

Authentication is an optional service of a protocol library. A library without that service can still
support offline players; selecting it does not enable MCProtocolLib's account workflow.
