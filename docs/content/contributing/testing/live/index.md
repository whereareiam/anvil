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
| `PlayerCapabilitiesSystemTest` | Actions and observations of every built-in player capability on the direct Paper server of each matrix version |
| `PlayerIdentityReconnectSystemTest` | Kicks, reconnects, and observed identity replacement on Paper `1.21.11` and `26.1.2` |
| `ProxyServerCompatibilitySystemTest` | Login, commands, and routes for every scenario of the live matrix |
| `ProcessRestartSystemTest` | Restarting the processes of every scenario of the live matrix |
| `ExternalExtensionSystemTest` | Externally packaged capabilities and platform-agent operations |
| `PartialScenarioLifecycleSystemTest` | Individual starts in a wired two-proxy, three-server environment; setup once, retained endpoints/workspaces, agent reconnection, and cleanup |

For example:

```shell
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*PlayerIdentityReconnectSystemTest'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*ExternalExtensionSystemTest'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*PartialScenarioLifecycleSystemTest'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*PlayerCapabilitiesSystemTest' -PanvilMatrixFilter='paper-1\.16\.5'
```

`anvilMatrixFilter` filters compatibility scenario names in the classes that iterate the matrix:
`ProxyServerCompatibilitySystemTest`, `ProcessRestartSystemTest` and `PlayerCapabilitiesSystemTest`. It
does not select test classes. Use `--tests` for class selection.

## Exercise exact protocol workers

For packet or worker changes, run the worker contracts before live coverage. They start a real worker for
every release in `mcprotocol-releases.toml` on the release's locked runtime closure, the `[[release.artifact]]`
modules Gradle resolves exactly as listed, and check the protocol number, the selected client segment and
that every built-in capability is installed, none reported unavailable. `ClientSegmentLinkageTest` reads the
linkage manifests of the built client segments and checks them against every release they serve, as the worker
does:

```shell
./gradlew :anvil-protocol:protocol-mcprotocol:mcprotocol-common:test --tests '*McProtocolWorkerContractTest'
```

Retain coverage for every release. [Adding a Minecraft version](../../minecraft-versions/index.md)
describes the release row, its pinned closure, segments, worker contracts, platform data, and the live
matrix that a new version needs.

Kicking, reconnect, and identity changes also require `PlayerIdentityReconnectSystemTest` for both
Paper versions it runs. Platform provider or forwarding changes require every affected server/proxy
combination.

## Keep verified data equal to the matrix

`CompatibilityScenarioFactory` defines the live matrix: every release key directly on Paper, `1.21.1`
and newer directly on NeoForge, and `1.21.11` and `26.1.2` also on Spigot and behind Velocity and BungeeCord.
The `verified` data of MCProtocolLib's releases and of each platform lists exactly the combinations it
runs. `CompatibilityScenarioFactoryTest` plans the matrix without starting a server and fails when they
differ; every build runs it through the `matrixTest` task, without `-Panvil.testMode=full`:

```shell
./gradlew :anvil-testkit:tests:server:matrixTest
```

## Run groups and full verification

`anvilTestTags` accepts a JUnit tag expression. Compatibility tests are tagged `compatibility`, and
`PlayerCapabilitiesSystemTest`, which starts a Paper server for every Minecraft version, is tagged
`capabilities`:

```shell
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full -PanvilTestTags=compatibility
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full -PanvilTestTags=capabilities
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full -PanvilTestTags='!compatibility & !capabilities'
./gradlew test -Panvil.testMode=full
```

CI runs the `capabilities` tag in one job and the untagged tests in another. It divides compatibility names into these mutually exclusive groups, one job each:

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
artifact inputs; JUnit task properties are not copied implicitly. The definitions are the direct
Paper `1.21.11` and `26.1.2` environments; the rest of the live matrix is built by
`CompatibilityScenarioFactory` for JUnit only and is not listed in the IDE.
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
