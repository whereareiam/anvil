---
title: Live tests
description: Verify native workers and real server/proxy behavior across the supported compatibility definitions.
---

Real platform tests live in `anvil-testkit/tests/server/src/test`. They start pinned distributions and run players
or agent operations against them. They form four suites, one package each, with tasks of their own, and run only
with `-Panvil.testMode=full`:

| Suite | Package | Task | What it checks |
|---|---|---|---|
| Release | `release` | `releaseTest-<minecraft>`, `releaseTest` for all | Every built-in player capability, the server's agent and a restart, against the direct Paper server of one release |
| Platform | `platform` | `platformTest` | Login, commands, routes and restarts on Spigot, behind Velocity and BungeeCord, and on NeoForge |
| Behaviour | `behavior` | `behaviorTest` | Lifecycles, sessions, kills, partial starts, routing, tooling and external extensions |
| Physics | `physics` | `physicsTest-<minecraft>`, `physicsTest` for all | Client physics of one release, judged by its Paper server, the Grim anti-cheat and the Sonar anti-bot |

```shell
./gradlew :anvil-testkit:tests:server:releaseTest-1.21.11 -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:platformTest -Panvil.testMode=full -PanvilPlatforms=velocity
./gradlew :anvil-testkit:tests:server:behaviorTest -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full
```

`test` runs the release, platform and behaviour suites: each release one after another, then the platform and
behaviour suites. The physics suite runs only on demand; neither `test` nor `check` starts it. The first run may
provision distribution JARs, protocol runtimes, and the Java installations required by the selected versions. These
checks accept the EULA through their test configuration. Do not add real online-account authentication to this
suite.

## Test a release

Each class of the release suite covers one capability, such as `MessagesReleaseTest` or `InventoryReleaseTest`, and
is annotated with `@Release`. Its tests run against the release that `releaseTest-<minecraft>` names, or the newest
release when a class runs on its own in the IDE. The release's server is kept for the task's JVM, so the whole
suite starts it once, while each test gets a scenario and players of its own: `FixtureServer.join()` connects a
player under a name no earlier test used. A test may leave the server with state the next tests do not depend on,
such as a player it made an operator; a failed test stops the server, and the next test starts a fresh one.

A capability a release predates, such as dialogs before `1.21.6`, is checked as absent there, and its other tests
are skipped through JUnit assumptions on `TestedRelease.isAtLeast(...)`:

```java
@Test
void hasNoCookiesBeforeTheyExisted(ScenarioContext anvil) {
	assumeFalse(TestedRelease.isAtLeast(COOKIES), "Cookies exist since " + COOKIES);

	assertFalse(new FixtureServer(anvil).join().hasCapability(Cookies.class));
}
```

Select one class with `--tests`:

```shell
./gradlew :anvil-testkit:tests:server:releaseTest-1.16.5 -Panvil.testMode=full --tests '*InventoryReleaseTest'
```

## Test platforms and behaviour

`-PanvilPlatforms` selects the platform suite's scenarios by the platform of their entrypoint, as a comma-separated
list of platform IDs: `spigot`, `velocity`, `bungeecord` or `neoforge`. Without it the suite runs every platform.
Behaviour tests start the scenarios they need through typed lookups, such as
`CompatibilityScenarioFactory.proxyScenario(Platforms.VELOCITY, Platforms.PAPER, version)`, never by a scenario's
name.

| Test class | Contract |
|---|---|
| `PlatformCompatibilitySystemTest` | Login, commands, agent extensions and backend switching on every platform scenario |
| `PlatformRestartSystemTest` | Restarting the entrypoint and a proxy's backend of every platform scenario |
| `NeoForgeServerSystemTest` | Login, console commands and kicks on every NeoForge release |
| `PlayerIdentityReconnectSystemTest` | Kicks, reconnects, and observed identity replacement on Paper `1.21.11` and `26.1.2` |
| `GrantedProfileObservationSystemTest` | Players whose usernames differ only in letter case, each observed by the profile the server granted it |
| `ExternalExtensionSystemTest` | Externally packaged capabilities and platform-agent operations |
| `PartialScenarioLifecycleSystemTest` | Individual starts in a wired two-proxy, three-server environment; setup once, retained endpoints/workspaces, agent reconnection, and cleanup |

## Test client physics

The physics suite checks that a player with client physics moves as a vanilla client does, so that servers and
anti-cheats accept it. `anvil-testkit/tests/server/physics-matrix.toml` lists its releases, those of the live matrix
whose block data Anvil has, and pins the two plugins it runs as judges; the build registers one
`physicsTest-<minecraft>` task per release from it. Each test class carries the JUnit tag of its judge, and
`-PanvilPhysicsJudges` selects judges as a comma-separated list; without it a task runs all three:

| Judge | Tag | Package | What it checks |
|---|---|---|---|
| The release's Paper server | `vanilla` | `physics.vanilla` | No position forced on the player, no "moved wrongly" or "moved too quickly", vanilla's fall damage, and the server seeing the player where its client is |
| Grim on a Paper server | `grim` | `physics.grim` | Everything the server checks, and not one flag from Grim's exact movement prediction |
| Sonar on a Velocity proxy | `sonar` | `physics.sonar` | Sonar's gravity and collision verification passes, and the player then reaches the backend |

```shell
./gradlew :anvil-testkit:tests:server:physicsTest-1.21.11 -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:physicsTest-1.16.5 -Panvil.testMode=full -PanvilPhysicsJudges=grim
./gradlew :anvil-testkit:tests:server:physicsTest -Panvil.testMode=full
```

The journeys run across a course that `PhysicsCourse` builds through the server console at `y = 150`, above every
release's own terrain: falls from 3, 10 and 24 blocks, a walk across stone, ice, slime and honey, a slab and stairs, a
fence and a wall to walk into, a block to jump onto, a lane to sprint-jump along, an edge to sneak to, a pool to swim
up and a ladder to climb. `CourseJourneyTest` holds them, and `VanillaJourneyTest` and `GrimJourneyTest` run them
against their judge. The server's console reports each position it forces on a player, which the fixture plugin logs
as `anvil:correction:<player>`, and tells where it sees the player and how much health it has left through
`data get entity`.

Each judge also has a control test whose player has client physics off and hovers where a client would fall:
`VanillaControlTest` expects the server to kick it for flying, `GrimControlTest` expects Grim to flag it, and
`SonarControlTest` expects Sonar to fail it. A control that passes shows that its judge is active on the release, so
a journey that the judge accepts proves something.

### Grim and Sonar

Grim and Sonar are GPL-licensed. The build downloads the pinned JAR of each from `physics-matrix.toml`, checks its
SHA-256, and passes it only to the physics tasks, which put it into the workspaces of their test processes; no Anvil
artifact carries either. Both run with their own default configuration, read from the pinned JAR, with these changes
only: neither looks for updates, Grim prints every flag to the console, and Sonar leaves out its vehicle check,
because a player with client physics does not steer a boat. Both run with an English locale, so their console lines
and messages are the ones the tests read.

| Judge | Pinned build | Releases it supports |
|---|---|---|
| GrimAC (Bukkit) | `2.3.74-61117c2` | Every release of the physics matrix; Paper 1.16.5 runs on Java 17 for it, since Grim's classes need Java 17 |
| Sonar (Velocity) | `2.1.48` | Every release of the physics matrix, behind the Velocity build of the live matrix; later builds need Adventure 5 and Velocity 3.6.0 or 4.0.0 |

`SonarControlTest` accepts only a failure of Sonar's movement checks. On releases from 1.20.5, Sonar disconnects
Anvil's players before those checks: from 1.20.5 to 1.20.6 it times out waiting for the client, and from 1.21 it
reports `didn't send client settings`, because Anvil's client sends its client information only once the game has
started, while a vanilla client sends it in the configuration phase. The Sonar tests of
those releases fail until the client does.

To change a pin, take the URL and the SHA-256 of the new build, check the `grim` and `sonar` columns of every release,
and run the control tests of each judge on every release before the journeys.

## Exercise exact protocol workers

For packet or worker changes, run the worker contracts before live coverage. They start a real worker for
every release in `mcprotocol-releases.toml` on the release's locked runtime closure, the `[[release.artifact]]`
modules Gradle resolves exactly as listed, and check the protocol number, the selected client segment and
that every built-in capability is installed, none reported unavailable. `ClientSegmentLinkageTest` reads the
linkage manifests of the built client segments and checks them against every release they serve, as the worker
does:

```shell
./gradlew :anvil-player:player-library:library-mcprotocol:mcprotocol-common:test --tests '*McProtocolWorkerContractTest'
```

Retain coverage for every release. [Adding a Minecraft version](../../minecraft-versions/index.md)
describes the release row, its pinned closure, segments, worker contracts, platform data, and the live
matrix that a new version needs.

Kicking, reconnect, and identity changes also require `PlayerIdentityReconnectSystemTest` for both
Paper versions it runs. Platform provider or forwarding changes require every affected server/proxy
combination.

## Keep verified data equal to the matrix

`anvil-testkit/tests/server/live-matrix.toml` lists the live matrix: the Paper build of every release key, the
releases that also run on Spigot and behind Velocity and BungeeCord with their Spigot checksums, the proxies' builds,
and the NeoForge releases. `CompatibilityScenarioFactory` builds the scenarios from it, and the build registers one
`releaseTest-<minecraft>` task per release from it. The `verified` data of MCProtocolLib's releases and of each
platform lists exactly the combinations the matrix runs. `CompatibilityScenarioFactoryTest` plans the matrix without
starting a server and fails when they differ; every build runs it through the `matrixTest` task, without
`-Panvil.testMode=full`:

```shell
./gradlew :anvil-testkit:tests:server:matrixTest
```

## Run the suites in CI

The live workflow reads the releases from `live-matrix.toml` and runs one job per release, so a new release gets its
own job without a workflow change. Three jobs run the platform suite, for Spigot and NeoForge, for Velocity and for
BungeeCord, and one job runs the behaviour suite. All of them run at once.

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
`CompatibilityScenarioFactory` from `live-matrix.toml` for JUnit only and is not listed in the IDE.
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
