---
title: Module boundaries
description: Locate the owning Gradle family and keep dependencies pointed toward public contracts.
---

Begin in the module that owns the behavior. `anvil-api` describes global lifecycle and registration;
a family's API describes its services and extension points. The launcher binds those scoped contracts.

| Family or module                                | Owns                                                                                                                |
|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `anvil-api`                                     | Global engine registration, scenario definitions/lifecycles, public process/player handles, and capability ownership |
| `anvil-engine`                                  | Scenario structure, setup hooks, active contexts, and overall cleanup ordering                                      |
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
| `anvil-launcher`                                | Global engine builder, default scenario factory, scoped-service bindings, and shaded assembly                      |
| `anvil-integration/integration-junit`                        | JUnit lifecycle, context injection, and outcome propagation                                                   |
| `anvil-tooling`                                 | Neutral session contracts, extensible actions, and foreground/IDE runner              |
| `anvil-integration/integration-gradle` | Shared Gradle declarations and explicit scenario, JUnit, and provider-selection adapters |
| `anvil-integration/integration-intellij/intellij-api` | IntelliJ integration contracts |
| `anvil-integration/integration-intellij/intellij-engine` | IntelliJ integration lifecycle and tooling logic |
| `anvil-integration/integration-intellij/intellij-gradle` | Native Gradle adapter |
| `anvil-integration/integration-intellij/intellij-ui` | IntelliJ SDK UI and presentation |
| `anvil-integration/integration-intellij/intellij` | Plugin descriptor and packaged assembly |
| `anvil-integration/integration-gradle/gradle-tooling` | Native Gradle task discovery, definition scanning, artifact binding, and prepared-launch production |
| `anvil-testkit/tests`                           | Cross-module runtime and live assertions                                                                            |
| `anvil-testkit/fixtures`                        | Independent consumer build producing real fixture JARs                                                              |
| `anvil-testkit/support`                         | Shared host-side artifact access and extension classloader lifetime                                                 |
| `build-logic`                                   | Java, testing, assembly, and publication conventions                                                                |
| `build-logic/settings`                          | Lean settings conventions: library registry, library repositories and library layout checks                         |

The IntelliJ integration implements lifecycle, preparation, account storage, and command history in
`intellij-engine`. `intellij-ui` owns catalog presentation, environment views, consoles, dialogs, navigation,
and tool windows. `intellij-gradle` implements the build-system discovery and preparation contract.
Both UI and Gradle production code depend on `intellij-api`; they do not import engine classes.

The tooling API exposes predefined wire operation contracts at its root:
`ScenarioOperations`, `EnvironmentOperations`, and `ProcessOperations`. `model.ToolingOperation`
pairs request and response schemas, and shared request payloads live beside their related models. The
runner binds these contracts to behavior through `ToolingOperationRegistry`; its reader has no
operation-specific decoding methods.

IntelliJ is a frontend in the logical tooling API family. The `integration-intellij` project declares
`architecture.family = projects.anvilTooling.path`, which its API and implementation modules inherit.
Its physical location remains under `anvil-integration`. `intellij-api` reuses the portable models
from `anvil-tooling/tooling-api` and adds IDE-specific contracts. Implementation dependencies remain
restricted to assembly modules; the runner executes in a separate project JVM.

The API groups contracts by feature below `me.whereareiam.anvil.integration.intellij`: build-system
discovery uses `source`, running environments use `environment`, retained output uses `log`, IDE
preferences and command history use `settings`, and account behavior uses `account`. `ScenarioCatalog`
remains at the root as the only scenario contract. Reusable values are grouped under `model`, closed types under `type`, and failures under `exception`;
model and type subpackages exist only when multiple files share one feature. Contracts may use native
IntelliJ project and disposal types. Persistence beans and runner mutations remain in the engine;
views receive immutable preference snapshots and retained `EnvironmentSession` handles. Catalog
state is read as one `CatalogSnapshot` whose lifecycle is a `CatalogState`; environment labels are derived from
the typed `EnvironmentState` exposed by `EnvironmentSession`; UI rendering only adds labels,
tones, and icons at the final presentation boundary.

Within `intellij-engine`, packages follow the behavior they own:

| Package | Responsibility |
|---------|----------------|
| `source` | `ProjectBuildIntegrations` coordinates build integrations; `SourceSelection` resolves saved source identities; `ProjectSourceDiscovery` owns detection, selection, and sync, delegating refresh-after-import decisions to `ImportRefreshPolicy`. |
| `scenario` | `ProjectScenarioCatalog` owns discovered definitions, loading state, and output. |
| (root) | `ChangeListeners` holds owner-scoped `subscribe(Runnable, Disposable)` registrations and their event-thread delivery. |
| `scenario.execution` | `ProjectEnvironmentLifecycle` owns active and retained environments; each `EnvironmentExecution` owns one snapshot and lifecycle, exposed by its permanently bound `RetainedEnvironmentSession`. |
| `log` | `StoredSessionLog` retains neutral output for catalog and environment views. |
| `tooling` | `ProjectToolingHost` arbitrates reuse and replacement, tracking each launch's `ProjectToolingHost.Purpose`; `ToolingLaunch` owns preparation, the runner, temporary files, and cleanup, advances through `ToolingLaunch.State`, and publishes one typed `ToolingLaunch.Outcome`. `ToolingLaunch.Inputs` supplies the preparation and account workspace. |
| `settings` | `PersistentPreferences` and `StoredCommandHistory` persist IDE preferences and accepted command history. |
| `account.authentication` | `BuildAccountAuthenticator` creates cancellable `ToolingAccountEnrollment` operations. |
| `account.persistence` | `ConfiguredAccountLibrary` applies source configuration; `AccountDirectoryRepository`, `AccountPoolRepository`, and `AccountWorkspace` own filesystem persistence and temporary runtime materialization. |
| `tooling.process` | `ToolingCommandReader` reads launch commands; `ToolingConnection` owns child processes, command input, output readers, and termination with diagnostic draining. |
| `tooling.protocol` | `ToolingClient` owns readiness, ordered requests, correlation, and terminal completion. `ToolingMessageCodec` maps typed envelopes and payloads generically using shared operation schemas from `tooling-api`. |

Within `intellij-ui`, locate a screen through its UI area, then its scoped components:

| Package | Responsibility |
|---------|----------------|
| `view.settings` | `AnvilSettingsConfigurable` integrates Apply/Reset; `SettingsForm` owns editable form values. |
| `view.window.main` | The native tool-window factory; `MainWindowController`, which owns content tabs and retention; and `ScenarioPresentation`, whose scenario identity, topology, and command rules are shared by every screen. |
| `view.window.main.catalog` | `CatalogController` composes discovery, execution, and command owners, which reach the screen through `CatalogView`; Kotlin owns screen and dialog presentation. |
| `view.window.main.catalog.tree` | `ScenarioTreeView` hosts the tree; `ScenarioTreeModel`, `ScenarioTreeExpansion`, and `ScenarioTreeRenderer` own projection, expansion memory, and rendering. |
| `view.window.main.environment` | Session tabs, section selection, and remembered view state; `overview`, `console`, and `players` hold its pages, and `action` their contributed actions. |
| `view.window.main.component` | Shared tool-window controls, the `details` inspector, and `status` presentation. |
| `view.window.account` | Project accounts and authentication dialogs, with scoped account components and pool editors. |
| `component.console` | `ConsolePresentation` renders one session log in one native console for one view, with its own process-handler adapter; tool-window tabs, Run tabs, and dialogs each create their own. |
| `runconfiguration` | `RunConfigurationService` resolves, validates, saves, and launches saved configurations; native type/configuration/editor adapters retain their IntelliJ callbacks. |
| `view.window.main.navigation` | `DefinitionNavigator` finds indexed definitions and opens the editor; `WorkspaceNavigator` opens process directories in the Project view or file manager. |

`RunConfigurationService` is registered per project by the assembly. Saving matches the source,
definition, scenario, and optional process identifiers, preserves an existing configuration's name,
and selects the saved configuration without starting an environment. The editor obtains source choices
through the same source resolver used for validation and launch. Launch revalidates saved identifiers,
uses a loaded descriptor only from the matching source, and delegates execution to `EnvironmentLifecycle`.
A source whose catalog has not been loaded can still launch its saved scenario through cold discovery.
`AnvilRunConfiguration` retains XML persistence and attaches the returned session's native console.
Running directly from the catalog does not create a saved configuration.

Panel and dialog definitions live in `intellij-ui/src/main/kotlin`: settings and dialog forms use Kotlin UI DSL,
while tool-window screens use Swing composition to preserve their specialized layouts. Java platform adapters
and controllers live in the matching packages under `src/main/java`. Keep layout, editable values,
presentation validation, and local control interactions in the form. Keep persistence, service calls,
subscriptions, history policy, and cancellation in Java. The boundary uses ordinary methods, existing
models, and Java callbacks such as `Runnable` and `Consumer`; it does not require a new API module.

For example, `SettingsForm` binds controls to a local draft, and `AnvilSettingsConfigurable` validates
and saves one immutable preference snapshot on Apply. Reset reloads saved preferences; closing without
Apply does not persist edits. `ActionInvocationForm` owns generated input widgets and result presentation, while the Kotlin
`ActionInvocationDialog` delegates session availability, invocation, and command history to its Java
controller. Kotlin account dialogs delegate account loading, enrollment, and pool persistence to Java
controllers. Account forms emit requests to those controllers. `EnvironmentSessionController` owns session
subscriptions and disposal. Environment, players, target-contributions, and console controllers own their
interactions; Kotlin panels compose controls and render display state. Tree/list renderers and visual Swing components live in Kotlin. Java remains appropriate for platform
console adapters such as `ConsolePresentation`, controllers, and lifecycle owners.

Both languages target Java 21; Kotlin uses the API level supported by the baseline IDE and its bundled
standard library. The UI module applies Kotlin's Lombok compiler plugin in addition to Java annotation
processing. This allows Kotlin views to consume Lombok-generated members from Java controllers in the
same module. Keep its version aligned with the Kotlin JVM plugin in the version catalog.

`CatalogController` composes three concrete collaborators for one catalog view. The discovery
controller observes source/catalog/preferences and owns the screen's source controls and status
messages. The execution controller owns the current session subscription and projects its snapshot
into the tree and inspector, matching source, definition, and scenario identities. Replacing that
subscription rejects notifications queued by an older session. The command controller captures the
current selection, rechecks availability, and delegates launch, save, and navigation requests. It
reports launch failures to the discovery controller for display; refresh, sync, or source selection
clear that feedback. Closing the view releases all three owners and their observers without stopping
the environment or project discovery.

Status values are also split from rendering. `ProcessSnapshot.state` and `SessionSnapshot.state`
remain the shared `ProcessState` and `SessionState` enums in `tooling-api`; unknown process wire values
map to `UNKNOWN`. The engine calculates the aggregate `EnvironmentState` exposed by
`EnvironmentSession`. Kotlin `StatusPresentation` supplies only final UI labels, descriptions, and
semantic tones, while `StatusIcon` and `StatusBadge` own Swing colors, overlays, accessibility, and
badge painting.

The catalog tree is intentionally split by responsibility. `ScenarioTreeModel` turns descriptors into typed
scenario/process nodes and restores stable selection. `ScenarioTreeExpansion` retains manual expansion
choices by source/definition/scenario identity while honoring the automatic expansion preference.
`ScenarioTreeRenderer` renders display text, platform labels, status overlays, tooltips, and accessibility
metadata. `ScenarioTreeView` only coordinates the Swing tree and delegates to those collaborators.

A component belongs at the narrowest scope containing its consumers. View controllers consume API
contracts; shared components do not depend on views. One-off layout helpers remain inner types.
Definition navigation waits for indexing and prefers the selected source directory before offering
a chooser for duplicate declarations. Workspace lookup refreshes files on a worker and delivers its
result on the IDE event thread. Both navigators stop delivering navigation after their view owner closes.
The engine publishes a read-only `SessionLog`; each `ConsolePresentation` subscribes for one view and
receives the retained entries first. Views share only the log, so closing a tool-window tab never disposes
the console a Run tab shows. The presentation prints output itself; Run configurations therefore do not
attach the console to its process handler, which would print every line twice. Tests mirror their owners:
import-refresh policy tests live in engine, while rendering and interaction tests live in UI.

`AccountWorkspace` instances are closed after the tooling process terminates, and cleanup failures are
reported. Persistent component names remain stable independently of Java implementation names so
saved settings survive refactoring.

`intellij` composes the plugin through service-interface bindings and extension registrations in
`META-INF/plugin.xml`. IntelliJ creates and disposes the registered services at application or
project scope. The assembly also supplies branding and packages the implementation modules.
Descriptor composition does not require a Java startup class.

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
| `mcprotocol-client/V1_18_2` and `V1_21_11` | One client segment per range of releases; `V1_21_11` also serves `26.1.2` |

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
