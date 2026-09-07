---
title: Authentication
description: Use offline players by default and opt into a protocol provider's private account workflow.
---

# Authentication

Players use offline authentication by default. This is the normal mode for repeatable automated
plugin tests. Online accounts are optional and must not become a CI dependency.

The selected protocol provider owns authentication. A provider can expose `ProtocolAuthentication`
without constructing a backend or player; an offline-only provider can leave that service absent.

## Sign in explicitly

```shell
./gradlew anvilLogin --auth-profile=main
./gradlew anvilLogout --auth-profile=main
```

Configure `anvil { protocol("mcprotocol") }` when several providers are installed. With one provider,
selection is automatic. A provider without interactive authentication reports that directly.
`--profile` is Gradle's build-profiler flag; the Anvil option is `--auth-profile`.

The MCProtocol provider uses Microsoft's device-code workflow and stores the refreshable profile
under `<cacheDirectory>/auth/<profile>.json`. On filesystems supporting POSIX permissions, directories
and files are restricted to their owner. Treat these files as credentials, not as ordinary caches.
Do not commit, upload, print, or copy them into scenario workspaces.

## Create an online player

```java
var alice = anvil.players().create(PlayerOptions.builder()
        .name("Alice")
        .authentication(AuthenticationMode.ONLINE)
        .authenticationProfile("main")
        .build());
```

The connection target must be online-mode, and the selected protocol must support online
authentication. The account's authenticated identity is provider-controlled; use the player/Server
identity observations to assert what the connection and platform actually report.

Anvil keeps access and refresh tokens out of Gradle inputs, command-line arguments, environment
variables, system properties, logs, and project workspaces. The MCProtocol worker receives its
access token only over private stdin. Per-run agent-session tokens are separate from account tokens.

Proxy forwarding has its own negotiated configuration. Online-mode proxies authenticate incoming
players; forwarded backend listeners use offline game authentication. See
[proxy platforms](../platforms/proxies/index.md).
