---
title: Module boundaries
description: Locate the owning Gradle family and keep dependencies pointed toward public contracts.
---

Begin in the module that owns the behavior. `anvil-api` describes global lifecycle and registration;
a family's API describes its services and extension points. The launcher binds those scoped contracts.

| Family or module                                | Owns                                                                                                                |
|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `anvil-api`                                     | Global engine registration, scenario definitions/lifecycles, public process/player handles, and capability ownership |
| `anvil-engine`                                  | Scenario structure, the process-property contract, setup hooks, active contexts, retention of process groups, and overall cleanup ordering |
| `anvil-environment/cache`                       | Independent cache API plus filesystem entry coordination and staged publication                                     |
| `anvil-environment/provisioning`                | Separate artifact, Java, and workspace APIs with acquisition, installation, layout, snapshots, and retention policy |
| `anvil-environment/execution/execution-managed` | Execution plans, preparation handoff, readiness, consoles, generation replacement, and ordered finalization         |
| Other `anvil-environment/execution` modules     | Execution-provider contracts, local processes, and Docker execution                                                 |
| `anvil-process/process-common`              | Distribution validation, effective Java/topology requirements, forwarding, and provider preparation/configuration                             |
| `anvil-process/process-api`                     | Platform-provider contracts, version data, artifact sources, and configuration contracts                            |
| `anvil-process/process-platform/platform-<name>` | One family per platform: its provider and, where it installs one, its platform agent assembly                |
| `anvil-player/player-common/src`                            | Player registration, per-player library and release selection, authentication compatibility, and observations     |
| `anvil-player/player-api`                   | Protocol library contracts, releases, players, protocol-owned channels and native worker contracts                 |
| `anvil-player/player-library/library-mcprotocol`            | The MCProtocolLib library: release data, client port, client segments, worker host and shell, and account storage |
| `anvil-capability/capability-api`                | Generic composition, neutral player contracts, descriptors, exceptions, and typed requests |
| `anvil-capability/capability-common`             | Dependency validation, agent-provider adaptation and agent extensions, logical process owners, player facades, and capability cleanup |
| `anvil-capability/capability-player/capability-player-api` | Protocol-backed player providers/contexts, channels/events, and native worker bindings |
| `anvil-capability/capability-player/capability-player-builtin` | Player capability families: feature APIs with their channel contracts, host providers, and Java Edition worker extensions |
| `anvil-capability/capability-process/capability-process-api` | Agent-backed process providers and scoped request-channel contexts |
| `anvil-capability/capability-process/capability-process-builtin` | Process capability families with agent-backed host wiring: Dispatch, and Players, which reports what one server or proxy knows about the players on it from its own agent's events |
| `anvil-capability/capability-default`            | Aggregate of every built-in capability |
| `anvil-process/process-protocol/process-protocol-api`                          | Shared operation descriptors, payloads, identities, and exceptions                                                   |
| `anvil-process/process-protocol/protocol-host/protocol-host-api`            | Host clients, connections, directories, and artifact lookup                               |
| `anvil-process/process-protocol/protocol-host/protocol-host-common`                       | Connections, stable process clients and sessions, directories, and observations in the Anvil JVM                    |
| `anvil-process/process-protocol/protocol-agent/protocol-agent-api`      | Native services, operation handlers, and embedded endpoint contracts                                                 |
| `anvil-process/process-protocol/protocol-agent/protocol-agent-common`                       | Embedded native operation dispatch, transport endpoint, and external-handler loading                                |
| `anvil-launcher`                                | Global engine builder, default scenario factory, scoped-service bindings, and shaded assembly; no rules of its own |
| `anvil-integration/integration-junit`                        | JUnit lifecycle, context injection, and outcome propagation                                                   |
| `anvil-tooling`                                 | Neutral session contracts, extensible actions, and foreground/IDE runner              |
| `anvil-integration/integration-gradle` | Shared Gradle declarations and explicit scenario, JUnit, and provider-selection adapters |
| `anvil-integration/integration-intellij` | IntelliJ plugin assembly: descriptor composing the feature modules, settings group, branding, and packaging |
| `anvil-integration/integration-intellij/intellij-api` | Contracts the IntelliJ features and build integrations share |
| `anvil-integration/integration-intellij/intellij-common` | Standard implementations of the shared IntelliJ services, and shared test fixtures |
| `anvil-integration/integration-intellij/intellij-scenario` | Scenario catalog, environments, consoles, run configurations, and editor run actions |
| `anvil-integration/integration-intellij/intellij-account` | Project and global accounts, pools, sign-in, and account directories |
| `anvil-integration/integration-intellij/intellij-gradle` | Native Gradle adapter |
| `anvil-integration/integration-gradle/gradle-tooling` | Native Gradle task discovery, definition scanning, artifact binding, and prepared-launch production |
| `anvil-testkit/tests`                           | Cross-module runtime and live assertions                                                                            |
| `anvil-testkit/fixtures`                        | Independent consumer build producing real fixture JARs                                                              |
| `anvil-testkit/support`                         | Shared host-side artifact access and extension classloader lifetime                                                 |
| `build-logic`                                   | Java, testing, assembly, and publication conventions                                                                |
| `build-logic/settings`                          | Lean settings conventions: library registry, library repositories and library layout checks                         |

The tooling API exposes predefined wire operation contracts at its root:
`ScenarioOperations`, `EnvironmentOperations`, and `ProcessOperations`. `model.ToolingOperation`
pairs request and response schemas, and shared request payloads live beside their related models. The
runner binds these contracts to behavior through `ToolingOperationRegistry`; its reader has no
operation-specific decoding methods.

IntelliJ is a frontend in the logical tooling API family. The `integration-intellij` project declares
`architecture.family = projects.anvilTooling.path`, which its modules inherit. Its physical location
remains under `anvil-integration`. The tooling runner executes in a separate project JVM; the plugin
never depends on its implementation.

### IntelliJ plugin modules

The plugin is split by feature. The family root is the plugin itself and holds no logic: its
`plugin.xml` includes one descriptor fragment per module and declares the **Settings → Tools → Anvil**
group, and its build packages the modules with the plugin icons.

```text
integration-intellij/
  src/                plugin.xml (includes the fragments below), settings group, branding
  intellij-api/       contracts the features and build integrations share
  intellij-common/    anvil-common.xml: their standard implementations; shared test fixtures
  intellij-scenario/  anvil-scenario.xml: catalog, environments, consoles, run configurations, editor
  intellij-account/   anvil-account.xml: accounts, pools, sign-in, account directories
  intellij-gradle/    anvil-gradle.xml: the Gradle build integration, loaded only with the Gradle plugin
```

Feature modules are implementations, so the architecture verifier lets them depend only on APIs. A
feature reaches shared behaviour through a service interface in `intellij-api`, bound by the module that
implements it; it never imports another feature. `intellij-api` holds:

| Contract | Implemented by | Used for |
|---|---|---|
| `BuildIntegration` extension point | `intellij-gradle` | Discovering, syncing, and preparing scenario sources of one build system |
| `BuildIntegrations` | `intellij-common` (`ProjectBuildIntegrations`) | All installed integrations as one project service |
| `ToolingCommands` | `intellij-common` (`ManifestToolingCommands`) | Reading a prepared launch manifest into runner and sign-in commands |
| `AccountStaging`, `StagedAccounts` | `intellij-account` (`ProjectAccountStaging`) | Giving a scenario run a private copy of the project's accounts |
| `ScenarioSource.DATA_KEY` | Scenario views | Telling actions contributed by other features which source a view shows |

Features also meet through the IDE's action system rather than through code. The scenario module
registers its actions and the groups that show them (`Anvil.Catalog.Toolbar`, `Anvil.Catalog.Popup`,
`Anvil.Environment.Toolbar`, `Anvil.Environment.Popup`); the account module adds **Anvil Accounts…** to the
catalog toolbar from its own fragment. The root `plugin.xml` includes the scenario fragment before the
account fragment, so the group exists when the account action joins it.

### Scenario module

`intellij-scenario` groups its packages by what they own, each with a `view` subpackage where it has
Swing screens. Concrete classes are project or application services (`@Service`); only the shared
contracts above are interfaces.

| Package | Responsibility |
|---------|----------------|
| `tooling` | `ToolingHost` arbitrates the project's single tooling launch and its `Purpose`; `ToolingLaunch` owns preparation, the runner, staged accounts, and cleanup and publishes one `Outcome`; `ToolingConnection` owns the child processes. `tooling.protocol` holds `ToolingClient` and `ToolingMessageCodec`. |
| `catalog` | `ScenarioCatalog` loads the selected source's scenarios into one `ScenarioCatalog.Snapshot`. `catalog.source.SourceDiscovery` owns detection, selection, and sync, delegating reload-after-import decisions to `ImportRefreshPolicy`. `catalog.view` holds `CatalogPanel`, its `CatalogController`, and the scenario tree. |
| `environment` | `EnvironmentLifecycle` owns the active environment and the retained sessions, and decides through `availability` whether a scenario or process can run and why not. Each `EnvironmentSession` is bound to one launch through `EnvironmentExecution` and reports an `EnvironmentSession.Status`. `environment.view` holds the session tab with its `overview`, `console`, `players`, and `contribution` pages. |
| `action` | The registered actions: run, stop, restart, save as run configuration, reload, and sync. They read `ScenarioDataKeys` and `ScenarioSource.DATA_KEY` from the invoking view and describe why they are unavailable. |
| `editor` | `ScenarioLineMarker` puts a Run icon beside definition classes recognised through UAST by `ScenarioDefinitions`. |
| `runconfiguration` | `AnvilRunConfiguration` stores a source, a definition class, and an optional process; `ScenarioConfigurationProducer` creates one from a class; `RunConfigurationService` saves, validates, and launches them through `EnvironmentLifecycle`. |
| `log`, `console` | `SessionLog` retains neutral output; each `ConsolePresentation` renders it in one native console. `CommandHistory` keeps accepted commands per `CommandHistory.Scope`. |
| `navigation` | `DefinitionNavigator` and `WorkspaceNavigator` open definition sources and process directories. |
| `settings` | `ScenarioSettings` and its **Scenarios** settings page. |
| `window` | The tool-window factory, `ToolWindowController` for its tabs, retention, and failure notifications, `ScenarioPresentation`, and shared view components. |

Views offer their selection through `UiDataProvider` and show registered action groups; they never
decide what a command does. Every entry point to running — the catalog, the editor gutter, run
configurations, and environment tabs — goes through `EnvironmentLifecycle.run`, or through the tab's own
`EnvironmentSession` when it already runs the scenario, so all of them follow the one-environment rule
and give the same reasons.

The account module follows the same split: `AccountLibrary` reads the project and global directories,
`storage` owns the account and pool files and staged copies, `AccountSignIn` runs one browser sign-in,
`settings` holds `AccountSettings` and the **Accounts** settings page, and `view` holds the account window.

Panels and dialogs are written in Kotlin, with Kotlin UI DSL for forms; controllers, services, and
platform adapters are Java in the matching packages. Kotlin targets the API level of the baseline IDE's
bundled standard library, which the `module-intellij` convention sets for every module that applies the
Kotlin plugin. The feature modules apply Kotlin's Lombok plugin so views can use members Lombok
generates for Java classes.

`examples/proof-of-patience` is a standalone consumer build. It consumes published artifacts and stays
outside root project discovery. Framework system assertions belong in `anvil-testkit`.

## Locate a capability or agent extension

Capability ownership and transport are separate. `Capability` and `CapabilityOwner` are global
identity/lookup contracts. `PlayerCapability` and `ProcessCapability` constrain the types accepted
by players and running processes. Their APIs do not require an agent transport.

Built-in families are grouped by participant, decided by who can normally do or know it, never by how it is
implemented. `capability-player/capability-player-builtin` contains what a client can do or see, which players
own, such as Session and Messages. `capability-process/capability-process-builtin` contains what a server or proxy
can do or know, which running processes own, such as Dispatch and Players; a proxy is server side. The groups
are no public owners themselves: public feature packages and artifact IDs remain feature-specific, such as
`builtin-players`.

Each built-in capability is a family folder `builtin-<feature>`: `<feature>-api` holds the public API and, for a
player capability, the contract between host and worker in its `channel` package; `<feature>-common` holds the host
provider in the feature packages; and a player capability's `<feature>-java` holds its worker extension, which
speaks the Java Edition protocol through the player's `JavaProtocol`. The library's client segments translate that
protocol for each release, so no built-in capability has segments of its own. The family root applies the
`module-capability` convention and only wires these members into the `builtin-<feature>` bundle. The convention publishes the members as `builtin-<member>`,
such as `builtin-movement-api`, and refuses a child that is not a member, a `common` or `util` package, and
code in the root of a family with common code or sides.

A family that reads its processes through their agents, such as dispatch or players, keeps its provider in
`<feature>-common` as well: it uses the platform agents' general operations, so it ships nothing into the
processes.

Capability providers share `CapabilityProvider`, `CapabilityContext`, and `CapabilityDescriptor`
from `capability-api`. Its `RequestChannel` performs a process's typed calls described by `ChannelOperation`; a
player's channel uses `player-api`'s own `OperationDescriptor`, because capability APIs never reach into the
player family's contracts the other way.
The shared `capability.api.player` package contains `PlayerCapabilityProvider` and
`PlayerCapabilityContext`: player identity/version, the requested and granted profiles, the connection,
declared dependencies, and lifetime.

The mechanism APIs add only the access their providers need. `capability-player-api` groups protocol player
factories and their context under `capability.protocol.api.player`; the context hands a provider the player's
`ProtocolChannel` from `player-api`. The player family owns that channel and the worker contracts: `player-api`
holds `WorkerExtension`, `PlayerBindingContext`, `OperationRegistry` and `WorkerBinding` under
`protocol.api.worker`, `OperationDescriptor<Q, R>` and `EventDescriptor<E>` under `protocol.api.model.channel`,
and `PlayerConnectionEvent` and `ViewRotation` under `protocol.api.model.player`. A descriptor pairs an ID with
the classes of its payloads; publishers and subscribers share it, and each emitted event is a separate payload
value. `capability-process-api` groups native request factories under `capability.agent.api.process`. Both
mechanism APIs depend on `capability-api`, without exposing the other mechanism or an agent transport API.

Agent transport and native handlers have separate contracts. `agent-client-api`, in `agent.client.api`,
contains clients, connections, directories, and artifact lookup. `agent-server-api`, in
`agent.server.api`, contains `AgentOperationProvider`, native platform service access, and transport
registration. Both receive operation descriptors and payloads from `agent-api`, in `agent.api`.
The client implementation and native handler run in different JVMs.

The launcher packages `agent-client`; platform-agent artifacts package the `agent-server`
implementation published as `agent`. Launcher bindings supply request channels to capability-owned agent factory adapters. Those adapters
participate in neutral capability composition without exposing agent transport APIs.

The agent role APIs depend on their explicitly shared ancestor `agent-api`, but cannot depend on
one another. Neither role nor the shared agent API depends on `anvil-api`; libraries can consume
agent transport and handler contracts independently of engine and capability contracts. The host
implementation uses global player observations where needed without exposing them through client APIs.

## Locate a protocol library

A protocol library is a family under `anvil-player/player-library/library-<library>`, and its folder holds the
library's release data, `<library>-releases.toml`. The settings plugin `build-libraries` registers
every such library. MCProtocolLib's family is laid out like this:

| Module | Owns |
|---|---|
| `library-mcprotocol` | Wiring-only root: shades `mcprotocol-common`, publishes `protocol-mcprotocol`, and owns `mcprotocol-releases.toml` and its `pinLibraryReleases` task |
| `mcprotocol-api` | The release-neutral client port `McProtocolClient<S>` that client segments implement |
| `mcprotocol-common` | `McProtocolLibraryProvider`, the release catalog, worker host and segment selection, the worker shell, and the private account store |
| `mcprotocol-java` | Source-free side folder whose `V*` children are the client segments |
| `mcprotocol-java/V1_18_2`, `V1_21_1`, `V1_21_11` and `V26_1_2` | One client segment per range of releases; `V26_1_2` extends `V1_21_11` and replaces only its interaction translation |

The worker shell never imports MCProtocolLib. Every class that does lives in a segment: a project named
after the release key it starts at, under a side folder named after its owner and library, such as
`mcprotocol-java` or an external capability's `teleport-mcprotocol`. A segment holds only the adapters of library-neutral ports
and their service descriptors; it never wires anything. Its package ends in the folder name in lower
case, such as `.v1_18_2`. For a release, the worker keeps the segment of each side with the greatest
start version that does not exceed the release key. A new segment is added only when a release breaks
the code of the previous one, which `checkSegmentLinkage` reports; see
[Adding a Minecraft version](../../minecraft-versions/index.md).

A capability that needs packets the protocol does not model can still repeat the pattern: its API declares a
port in its `packet` package, and its `<feature>-mcprotocol` side holds the wiring extension and the `V*` segments
that implement it; see [Protocol adapters](../../../extending/capabilities/protocol-adapters/index.md).

## Preserve dependency direction

`anvil-api` has no Anvil project dependency, and the engine depends only on `anvil-api`. Ordinary
implementations and family APIs may depend on their own family's APIs and `anvil-api`. They must
not import another family's API through either a direct dependency or an API re-export.

`anvil-environment` groups directories; it does not combine cache, execution, artifacts, Java, and
workspaces into one API family. Cache operations remain in `cache-api`, HTTP acquisition in
`artifact-api`, Java installation in `java-api`, and workspace lifecycle in `workspace-api`.
The engine has no dependency on these APIs. Concrete SDKs, serialization, and transports also stay
outside the global contract.

When another family needs part of a service, define a focused input or preparation boundary in the
consumer's API. For example, execution requests a local executable through `LocalRuntimePreparation`
and guards image metadata through `ImageLocks`; launcher adapters bind those requests to Java and
cache APIs. Docker keeps its image selection, cache layout, and validation order.

Implementations depend on APIs and external libraries, not sibling implementations. Introduce a
shared API only for a real extension or module boundary. Ordinary collaborators such as a
configuration writer and a distribution resolver remain concrete within their owning implementation.

Assembly modules can bind and package implementations. Keep service construction in the launcher and
external integrations in `anvil-integration`. Classifying an ordinary implementation as an assembly
does not resolve a misplaced dependency. Service descriptors belong with the implementation or
assembly that supplies the service, and shading must merge them. Feature wiring bundles register their
providers and worker extensions; the native feature implementation consumes its own API and actual SDK.

## Consume a scoped API directly

`cache-api` and the artifact API published as `provisioning-api` have no dependency on `anvil-api`.
A library can consume them without constructing an engine or installing the launcher.

The published `cache-filesystem` implementation supplies `cache-api` transitively. For a Java
application, use this build fragment with your chosen `anvilVersion` property and the usual Maven
repositories:

```kotlin
plugins {
	application
}

val anvilVersion = providers.gradleProperty("anvilVersion").get()

dependencies {
	implementation("me.whereareiam.anvil:cache-filesystem:$anvilVersion")
}

application {
	mainClass.set("CachedText")
}
```

Put this complete program in `src/main/java/CachedText.java` and run `./gradlew run`. It publishes a
staged entry and prints `hello` after reopening it:

```java
import me.whereareiam.anvil.environment.cache.api.model.CacheKey;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CachedText {
	public static void main(String[] args) throws IOException {
		var cache = new FileCache(Path.of("build", "example-cache"));
		var key = CacheKey.builder().namespace("example-text").value("greeting").suffix(".txt").build();
		try (var entry = cache.open(key); var write = entry.stageFile()) {
			Files.writeString(write.path(), "hello");
			write.commit();
		}
		try (var entry = cache.open(key)) {
			System.out.println(Files.readString(entry.path()));
		}
	}
}
```

Neither cache artifact requires `anvil-api` or an engine. A library that accepts a caller-supplied
`Cache` can depend on `cache-api` alone. Entry leases own coordination, and staged writes own publication.
An artifact consumer similarly borrows `ArtifactResolver` from its own API; it does not need to know
which storage implementation the resolver uses.

## Keep packages navigable

Use feature-oriented packages. Keep contracts at the feature root, reusable public values under
`model`, and enums or closed value types under `type`. Physical directories match package names.
Small implementation-local carriers can be inner records; public models use separate top-level files.
A package needs at least two files unless it is one of the fixed role packages `model`, `type`,
`exception`, and `packet`. A capability with segments keeps its release port in `packet`, even when the
port is the package's only file, so the port has the same place in every family. A segment's version
package, such as `v1_18_2`, and a library side's wiring package, such as `teleport.mcprotocol`, hold one
class by design and are exempt as well. Avoid catch-all
`util` or `common` packages; common code lives in the feature packages.

Within workspace provisioning, `directory` owns confined file access and layout, `preparation` owns
plan validation and prepared sessions, and `snapshot` owns snapshot identity and storage. The root
`DefaultWorkspaceProvisioner` coordinates those responsibilities. Tests mirror the production
packages; immutable layout and preparation contracts remain in `workspace-api`.

Use imports, JetBrains nullability annotations, and useful multiline Javadocs on public APIs. Prefer
immutable values and Lombok for routine construction. Java source uses tabs; dependencies and other
shared build behavior follow `build-logic` conventions and the grouped version catalog.

## Verify a boundary change

Update project dependencies, public imports, service filenames, descriptor contents, and published
artifact wiring together. Run the owning module's tests and `./gradlew verifyArchitecture`. For a
packaging change, also verify [runtime discovery](../../testing/unit-runtime/index.md) and a consumer
build against the produced artifacts.

The architecture task inspects production project dependencies, including inherited configurations,
API exports, and runtime or embedded dependencies. It does not inspect external Maven artifacts or
prove public signatures and runtime behavior correct. Review those boundaries alongside the check.
