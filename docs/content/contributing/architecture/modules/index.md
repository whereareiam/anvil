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
| `anvil-platform/platform-planning`              | Distribution validation, effective Java/topology requirements, forwarding, and provider preparation/configuration                             |
| Other `anvil-platform` modules                  | Platform-provider contracts, implementations, and platform-agent assemblies                                         |
| `anvil-protocol/src`                            | Player registration, per-player library and release selection, authentication compatibility, and observations     |
| `anvil-protocol/protocol-api`                   | Protocol library contracts, releases, players, protocol-owned channels and native worker contracts                 |
| `anvil-protocol/protocol-mcprotocol`            | The MCProtocolLib library: release data, client port, client segments, worker host and shell, and account storage |
| `anvil-capability/src`                          | Dependency validation, agent-provider adaptation, logical process owners, player facades, and capability cleanup                                |
| `anvil-capability/capability-api`                | Generic composition, neutral player contracts, descriptors, exceptions, and typed requests |
| `anvil-capability/capability-protocol-api`         | Protocol-backed player providers/contexts, channels/events, and native worker bindings |
| `anvil-capability/capability-agent-api`          | Agent-backed process/player providers and scoped request-channel contexts |
| `anvil-capability/capability-builtin/player`     | Player capability families: feature APIs, library-neutral providers and bindings, and library sides with segments    |
| `anvil-capability/capability-builtin/process`    | Process capability families with agent-backed host wiring, including Console                                        |
| `anvil-agent/agent-api`                          | Shared operation descriptors, payloads, identities, and exceptions                                                   |
| `anvil-agent/agent-client/client-api`            | Host clients, connections, directories, and artifact lookup                               |
| `anvil-agent/agent-client`                       | Connections, stable process clients and sessions, directories, and observations in the Anvil JVM                    |
| `anvil-agent/agent-server/agent-server-api`      | Native services, operation handlers, and embedded endpoint contracts                                                 |
| `anvil-agent/agent-server`                       | Embedded native operation dispatch, transport endpoint, and external-handler loading                                |
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

`capability-builtin/player` contains player features. `capability-builtin/process` contains process
features with agent-backed wiring, including Console; that directory groups families by capability owner
and is not a separate public owner. Public feature packages and artifact IDs remain feature-specific.

Each built-in capability is a family folder: `<feature>-api` holds the public API (and, for packet
capabilities, the library-neutral port in its `packet` package), `<feature>-common` holds the host provider
and worker binding in the feature packages, and `<feature>-<library>` adapts the feature to one protocol
library through `V*` segments. The family root applies the `module-capability` convention and only wires these
members into the `builtin-<feature>` bundle. The convention publishes the members as `builtin-<member>`,
such as `builtin-movement-api`, and refuses a child that is not a member, a `common` or `util` package, and
code in the root of a family with common code or library sides.

A process family such as console has no common code or library sides: its root holds the agent-backed
provider. That provider binds the family API to agent operations, and only an assembly may depend on both
the capability and the agent APIs, so the root is its owner.

Capability providers share `CapabilityProvider`, `CapabilityContext`, and `CapabilityDescriptor`
from `capability-api`. Its `RequestChannel` performs typed calls described by `ChannelOperation`, and `OperationRegistry`
registers handlers for those same descriptors.
The shared `capability.api.player` package contains `PlayerCapabilityProvider`,
`PlayerCapabilityContext`, and `CapabilityPlayer`: player identity/version, observations, declared
dependencies, and lifetime. The built-in `Server` provider uses that neutral observation contract.

The mechanism APIs add only the access their providers need. `capability-protocol-api` groups
protocol player factories, channels/events, and native worker contracts under
`capability.protocol.api.player`. `PlayerConnectionEvent` lives under
`capability.protocol.api.model.player`. `ViewRotation` and `EventDescriptor<E>` live directly under
`capability.protocol.api.model`: the former holds yaw and pitch, and the latter pairs an event ID
with the class of its payload. Publishers and
subscribers share the descriptor; each emitted `PlayerConnectionEvent` is a separate payload value.
`PlayerBindingContext` remains in the worker package
because it supplies native SDK/lifecycle access during binding. `capability-agent-api` groups native
request factories under
`capability.agent.api.player` and `capability.agent.api.process`. Both mechanism APIs depend on
`capability-api`, without exposing the other mechanism or an agent transport API.

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

A protocol library is a family under `anvil-protocol/protocol-<library>`, and its folder holds the
library's release data, `<library>-releases.toml`. The settings plugin `build-libraries` registers
every such library. MCProtocolLib's family is laid out like this:

| Module | Owns |
|---|---|
| `protocol-mcprotocol` | Wiring-only root: shades `mcprotocol-common`, publishes `protocol-mcprotocol`, and owns `mcprotocol-releases.toml` and its `pinLibraryReleases` task |
| `mcprotocol-api` | The release-neutral client port `McProtocolClient<S>` that client segments implement |
| `mcprotocol-common` | `McProtocolLibraryProvider`, the release catalog, worker host and segment selection, the worker shell, and the private account store |
| `mcprotocol-client` | Source-free side folder whose `V*` children are the client segments |
| `mcprotocol-client/V1_18_2`, `V1_21_1` and `V1_21_11` | One client segment per range of releases; `V1_21_11` also serves `26.1.2` |

The worker shell never imports MCProtocolLib. Every class that does lives in a segment: a project named
after the release key it starts at, under a side folder named after its owner and library, such as
`mcprotocol-client` or `movement-mcprotocol`. A segment holds only the adapters of library-neutral ports
and their service descriptors; it never wires anything. Its package ends in the folder name in lower
case, such as `.v1_18_2`. For a release, the worker keeps the segment of each side with the greatest
start version that does not exceed the release key. A new segment is added only when a release breaks
the code of the previous one, which `checkSegmentLinkage` reports; see
[Adding a Minecraft version](../../minecraft-versions/index.md).

Capability families repeat the pattern: `<feature>-api` declares the port in its `packet` package, and
`<feature>-mcprotocol` holds the wiring extension and the `V*` segments that implement it.

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
assembly that supplies the service, and shading must merge them. Feature wiring bundles register
capability/worker bridges; the native feature implementation consumes its own API and actual SDK.

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
`exception`, and `packet`. Every packet capability keeps its release port in `packet`, even when the
port is the package's only file, so the port has the same place in every family. A segment's version
package, such as `v1_18_2`, and a library side's wiring package, such as `movement.mcprotocol`, hold one
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
