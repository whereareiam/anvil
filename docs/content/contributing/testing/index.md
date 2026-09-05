---
title: Testing Anvil
description: Find unit, runtime-integration, live-server, and fixture code by its owning module.
---

# Testing Anvil

Keep tests in the narrowest owning module. Ordinary unit and focused integration tests live in that
module's `src/test`, normally with the same package as the class or feature under test. A module-local
integration test can use real sockets, temporary files, protocol workers, or Gradle TestKit without
belonging to the real-server suite.

## Cross-module testing modules

| Module under `anvil-testing` | What it verifies |
|---|---|
| `testing-runtime` | Provider discovery, selection, external JAR loading, and capability composition without Minecraft |
| `testing-server` | Real server/proxy behavior: sessions, capabilities, routes, and external agent operations |
| `testing-fixtures/fixtures-server-plugin` | Plugin commands and native observations installed into test servers |
| `testing-fixtures/fixtures-external-extension` | External backend/capability/agent example and shared JAR/class-loader setup |

Test modules use ordinary `src/test/java`. Fixture modules use `src/main/java` and `src/main/resources`
and produce real JARs. Individual tests do not each need a module; separate modules express different
dependencies, packaging, and execution requirements.

## Choose the test by behavior

- `PlatformProviderDiscoveryIntegrationTest` verifies the installed platform providers.
- `ExternalProviderDiscoveryIntegrationTest` verifies selection, dependency diagnostics, and offline authentication defaults.
- `PlayerIdentityReconnectSystemTest` verifies kicks, reconnects, and identity replacement.
- `PlayerCapabilitiesSystemTest` verifies capability actions observed by a real server.
- `ProxyServerCompatibilitySystemTest` verifies direct and proxy routes across the supported versions.
- `ExternalExtensionSystemTest` calls a real platform agent through externally packaged capabilities.

The server module groups these under `session`, `capability`, `routing`, `extension`, and `scenario`.
The runtime module groups discovery checks under `provider`. The same test class does not mix live
and non-live methods selected through tags.

## Run checks

```shell
./gradlew verifyArchitecture
./gradlew :anvil-testing:testing-runtime:test
./gradlew build
```

Normal builds skip the whole `testing-server:test` task. Enable real platforms explicitly:

```shell
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*ExternalExtensionSystemTest'
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
./gradlew test -Panvil.testMode=full
```

Consumer journeys run separately after local publication with
`./gradlew -p examples/proof-of-patience anvilTest`. The matrix filter applies to
compatibility cases only; use `--tests` for selecting a class. Failed live workspaces are retained
under the server module's `build/anvil` directory. Inspect each process's `anvil-console.log` after
startup or routing failures.

## Fixture ownership

Default to case-specific fixtures beside the test. Share setup only when multiple tests need it or
when a real JAR boundary is part of the behavior under test. The server fixture supplies common
observations; the external-extension fixture proves loading and composition through public APIs.
Its in-process backend is not a second production Minecraft client implementation.

The external-provider checks cover automatic selection with default adapters installed, ambiguous
selection, missing dependencies, and the offline authentication default. Live extension tests cover
explicit selection alongside MCProtocol, agent operations on real Paper, cleanup, and observations
without a Session adapter. To run those suites from the Anvil repository:

```shell
./gradlew :anvil-testing:testing-runtime:test --tests '*ExternalProviderDiscoveryIntegrationTest'
./gradlew :anvil-testing:testing-server:test --tests '*ExternalExtensionSystemTest' -Panvil.testMode=full
```

The Gradle authentication tests build a fake provider JAR and verify login/logout dispatch and
configuration-cache reuse. They never authenticate a real online account.

Resolve fixture JARs through Gradle project artifacts, not guessed sibling build paths. Keep the
proof-of-patience example consumer-shaped; framework-specific assertions belong in these modules.
