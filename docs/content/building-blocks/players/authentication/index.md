---
title: Authentication
description: Use repeatable offline players or an explicitly configured private online account for local tests.
---

Offline players are the default for automated plugin tests. Use an online account only when a local
journey specifically depends on authenticated Minecraft identity.
Keep real account sign-in out of CI.

## Sign in to a named profile

Use an [installed MCProtocol provider](../../../getting-started/installation/index.md).
When several providers are installed, select it in `build.gradle.kts` with
`anvil { protocol("mcprotocol") }`.
Then run:

```shell
./gradlew anvilLogin --auth-profile=main
```

Follow the provider's device-code instructions in your browser. The MCProtocol provider stores the
refreshable profile under `<cacheDirectory>/auth/main.json`. It restricts directories and files to the
owner on filesystems supporting POSIX permissions.
The CLI option is `--auth-profile`; `--profile` belongs to Gradle's build profiler.

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
				.authenticationProfile("main")
				.build());
		var session = player.capability(Session.class);
		session.connect();
		session.connected();
		return player;
	}
}
```

Call `AuthenticatedPlayers.connect(anvil)` from a local test in the same package and run that class with
`./gradlew anvilTest --tests 'your.package.YourOnlineTest'`. A connected session establishes that
the account authenticated at the target. Check [identity and server observations](../capabilities/server/index.md)
for assertions about what the platform sees; the authenticated identity is controlled by the account.

## Keep the account store private

Treat the authentication directory as credentials. Exclude it from source control, diagnostic uploads,
and shared cache archives. Access and refresh tokens do not belong in Gradle inputs, CLI arguments,
environment variables, system properties, or scenario files. MCProtocol sends the access token to its
worker through private stdin. Agent session credentials are separate per-run values.

Remove the locally stored profile with:

```shell
./gradlew anvilLogout --auth-profile=main
```

Authentication is an optional service of the selected protocol provider. A provider without that
service can still support offline players; selecting it does not enable MCProtocol's account workflow.
