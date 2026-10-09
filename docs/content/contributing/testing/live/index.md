---
title: Live tests
description: Verify native workers and real server/proxy behavior across the supported compatibility definitions.
---

Real platform tests live in `anvil-testkit/tests/server/src/test`. They start pinned distributions
and run players or agent operations against them. Enable the whole task with `-Panvil.testMode=full`:

```shell
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full
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
| `PartialScenarioLifecycleSystemTest` | Individual starts in a wired two-proxy, three-server environment; setup once, retained endpoints/workspaces, agent reconnection, and cleanup |

For example:

```shell
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*PlayerIdentityReconnectSystemTest'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*ExternalExtensionSystemTest'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*PartialScenarioLifecycleSystemTest'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
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
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full -PanvilTestTags=compatibility
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full -PanvilTestTags='!compatibility'
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

## Inspect framework environments in the IDE

Open the Anvil repository with the [IntelliJ plugin](../../../integrations/intellij/installation/index.md)
and open its Anvil tool window. It imports the project model when needed and loads the scenario definitions
automatically. The `anvil-testkit/tests/server` module applies the standard Anvil plugin to its `src/anvil`
compatibility definitions. The plugin itself is resolved at the build's `anvilVersion`, so publish it
first with `./gradlew publishToMavenLocal`; the Anvil runtime it adds is substituted with the current
source projects.

Preparation compiles those definitions and resolves the declared fixture variants from the
independent `anvil-testkit/fixtures` build. Its root-composite invocation substitutes current public
Anvil source projects. The declaration forwards exact `anvil.testkit.fixture.*` paths as tracked
artifact inputs; JUnit task properties are not copied implicitly. The definitions contain twelve
environments: Paper, Spigot, Velocity→Paper, Velocity→Spigot, BungeeCord→Paper, and BungeeCord→Spigot
for both `1.21.11` and `26.1.2`. The IDE presents the eight wired environments and their components;
the matching standalone presets stay available to the direct-server test matrix and saved runs.
Loading the definitions starts no Minecraft processes. Selecting Run starts
the chosen environment with the module's declared properties and artifacts. Selecting a component
exposes its individual Start action; the prepared environment retains the full wiring while its
other JVMs remain stopped.

These scenario definitions do not create players automatically. Individual JUnit methods create
their players and method-scoped fixtures, and still use the live-test commands above. Starting an
IDE environment does not run those methods or their assertions.

## Investigate a failure

Read the test failure and captured process output first. Startup, restart, or cleanup failures can
retain diagnostic workspaces under the server module's `build/anvil` directory; inspect each
process's `anvil-console.log`. Check the distribution pin, Java requirement, readiness, and forwarding
configuration before increasing timeouts.

The JUnit integration supplies failed test outcomes to finalization. Tests that own a prepared
context directly, including `PartialScenarioLifecycleSystemTest`, pass
their success flag to `finish(successful)` so failed caller work reaches retention policy. Collect
the diagnostics relevant to your assertion in the test output. Consumer journeys
run separately after [local publication](../../publishing/index.md#local-publication).
