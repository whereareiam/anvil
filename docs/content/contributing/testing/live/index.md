---
title: Live tests
description: Verify native workers and real server/proxy behavior across the supported catalog.
---

Real platform tests live in `anvil-testing/testing-server/src/test`. They start pinned distributions
and run players or agent operations against them. Enable the whole task with `-Panvil.testMode=full`:

```shell
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full
```

The first run may provision distribution JARs, protocol runtimes, and the Java installations required
by the selected versions. These checks accept the EULA through their test configuration. Do not add
real online-account authentication to this suite.

## Select the behavior you changed

| Test class | Contract |
|---|---|
| `PlayerCapabilitiesSystemTest` | Actions and observations through real servers |
| `PlayerIdentityReconnectSystemTest` | Kicks, reconnects, and observed identity replacement |
| `ProxyServerCompatibilitySystemTest` | Supported direct and proxy routes |
| `ExternalExtensionSystemTest` | Externally packaged capabilities and platform-agent operations |

For example:

```shell
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*PlayerIdentityReconnectSystemTest'
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*ExternalExtensionSystemTest'
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
```

`anvilMatrixFilter` filters compatibility scenario names. It does not select other test classes.
Use `--tests` for class selection.

## Exercise exact protocol workers

For packet or worker changes, run the catalog-version worker contracts before live coverage:

```shell
./gradlew :anvil-protocol:protocol-mcprotocol:test --tests '*ProtocolWorkerContractTest'
```

Retain coverage for both catalog versions. Adding a supported version requires an exact runtime
artifact URL and SHA-256, protocol number, Java requirement, binding family, worker and capability
contracts, every supported direct/proxy route, and updated supported-version documentation.

Kicking, reconnect, and identity changes also require `PlayerIdentityReconnectSystemTest` for both
versions. Provider or forwarding changes require every affected server/proxy combination.

## Run groups and full verification

`anvilTestTags` accepts a JUnit tag expression. Compatibility tests are tagged `compatibility`:

```shell
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full -PanvilTestTags=compatibility
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full -PanvilTestTags='!compatibility'
./gradlew test -Panvil.testMode=full
```

CI divides compatibility names into these mutually exclusive groups:

| Group | Matrix filter |
|---|---|
| Direct servers | `^(?!velocity-|bungee-).*` |
| Velocity | `^velocity-.*` |
| BungeeCord | `^bungee-.*` |

Keep scenario prefixes and CI filters aligned when adding another proxy family. Tag or matrix
selection does not enable live tests without full mode.

## Investigate a failure

Read the test failure and captured process output first. Startup, restart, or cleanup failures can
retain diagnostic workspaces under the server module's `build/anvil` directory; inspect each
process's `anvil-console.log`. Check the distribution pin, Java requirement, readiness, and forwarding
configuration before increasing timeouts.

An assertion failure by itself does not currently guarantee retention of an otherwise successful
scenario. Collect the diagnostics relevant to your assertion in the test output. Consumer journeys
run separately after [local publication](../../publishing/index.md#local-publication).
