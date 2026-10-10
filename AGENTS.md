# AGENTS.md

## Purpose and scope

Anvil is a Java 21 testing framework for local Minecraft Java Edition processes and native-protocol
players. Preserve reproducibility, protocol fidelity, complete cleanup, and useful failure diagnostics.
NeoForge is the supported mod loader. Kubernetes, SSH/hosted orchestration, Fabric, Sponge, Bedrock, rendering,
pathfinding, autonomous AI, and crafting automation are outside the current project scope.

## Working in this repository

- Inspect the working tree before editing. Preserve existing work and the user's staged review baseline.
  Do not stage, commit, or discard changes unless requested.
- Start in the narrowest owning module. Read its build file, adjacent implementations, and tests.
- Treat API and provider contracts as compatibility-sensitive. Update callers, service descriptors,
  packaging, tests, and documentation together when changing a contract or package.
- Prefer focused changes that remove a concrete dependency or clarify ownership. Do not add a generic
  abstraction, fallback, or wrapper just to move complexity elsewhere.
- Use interfaces at real module and extension boundaries. Keep ordinary local collaborators concrete.
- Names describe responsibilities and, when relevant, their technology: for example,
  `McProtocolMovementExtension`, `SpigotDistributionResolver`, and `WorkspacePlanValidator`.

## Module ownership

| Area                                                                  | Responsibility                                                                                                                                                                      |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `anvil-api`                                                           | Global engine registration, scenario definitions/lifecycles, public process/player handles, and capability owner identities; no scoped services or project dependencies             |
| `anvil-engine`                                                        | Global builder/registration, scenario structure validation, extensions, setup hooks, default ready contexts, active contexts, and owned-resource cleanup; depends only on anvil-api |
| `anvil-environment`                                                   | Organizational grouping for independent cache, provisioning, and execution families                                                                                                 |
| `anvil-environment/cache/cache-api`                                   | Independently consumable cache entry and staged-publication contracts; no anvil-api dependency                                                                                      |
| `anvil-environment/cache`                                             | Filesystem cache locations, cross-process entry coordination, and staged publication                                                                                                |
| `anvil-environment/provisioning/provisioning-artifact/artifact-api`   | Independently consumable artifact resolution, acquisition, and storage contracts; no anvil-api or cache-api dependency                                                              |
| `anvil-environment/provisioning/provisioning-artifact`                | Bounded HTTP acquisition, metadata refresh, and artifact checksum verification                                                                                                      |
| `anvil-environment/provisioning/provisioning-java/java-api`           | Java installation, validation, package acquisition, and installation-storage contracts                                                                                              |
| `anvil-environment/provisioning/provisioning-java`                    | Local JDK inspection, Foojay resolution, and user archive installation                                                                                                              |
| `anvil-environment/provisioning/provisioning-workspace/workspace-api` | Immutable workspace layouts, preparation/finalization, and snapshot storage contracts                                                                                               |
| `anvil-environment/provisioning/provisioning-workspace`               | Run and process directory layout, workspace validation and preparation, snapshot storage, retention policy, and confined cleanup                                                    |
| `anvil-environment/execution/execution-api`                           | Execution plans, providers, endpoints, processes, prepared input/launch lifetimes, runtime preparation, and image coordination                                                      |
| `anvil-environment/execution/execution-managed`                       | Complete topology allocation, prepared-input handoff, readiness, consoles, generation replacement, and ordered finalization; no foreign scoped APIs                                 |
| `anvil-environment/execution/execution-local`                         | Host process execution and local endpoints                                                                                                                                          |
| `anvil-environment/execution/execution-docker`                        | Typed Docker Engine execution, images, networks, and container endpoints                                                                                                            |
| `anvil-environment/yggdrasil-mock`                                    | Optional test-only Yggdrasil session server and profile lookup; one possible target of the session-server declarations, depends only on anvil-api                                   |
| `anvil-launcher`                                                      | Public default builder, property decoding, scoped-service adapters, default ScenarioFactory/per-run assembly, engine-lifetime process retention, native worker bridge, and shaded packaging |
| `anvil-capability/capability-api`                                     | Owner-neutral composition, typed requests/handler registration, and neutral player identity/observations/dependency/lifetime contracts                                              |
| `anvil-capability/capability-protocol-api`                            | Protocol-backed player providers/contexts, channels/events, and native worker contracts                                                                                             |
| `anvil-capability/capability-agent-api`                               | Agent-backed process/player providers and scoped request-channel contexts; shared capability API only                                                                               |
| `anvil-capability`                                                    | Shared dependency validation/composition/cleanup for capability owners, process/player facades, agent-provider adaptation, codecs, and worker bindings                              |
| `anvil-capability/capability-builtin/player/<feature>`                | Player capability family whose wiring-only root publishes the shaded `builtin-<feature>` bundle                                                                                     |
| `anvil-capability/capability-builtin/player/<feature>/<feature>-api`  | Public feature API (`builtin-<feature>-api`) and, for packet capabilities, the stateless release port in `.packet`                                                                  |
| `anvil-capability/capability-builtin/player/<feature>/<feature>-common` | Embedded, unpublished host provider, operation descriptors, and library-neutral binding in the feature root package                                                               |
| `anvil-capability/capability-builtin/player/<feature>/<feature>-<library>` | Segmented side folder holding the library's `WorkerExtension` (`builtin-<feature>-<library>`)                                                                                  |
| `anvil-capability/capability-builtin/player/<feature>/<feature>-<library>/V*` | Segments that implement only the feature's port for the library releases from their key on                                                                                 |
| `anvil-capability/capability-builtin/process/<feature>`               | Process capability family: `<feature>-api` and a root holding the agent-backed provider, independent of simulated players                                                           |
| `anvil-capability/capability-builtin/default`                         | Aggregate of every built-in player and process capability                                                                                                                           |
| `anvil-protocol/protocol-api`                                         | Protocol library providers and releases, player creation/composition, protocol-owned channels/workers, pinned runtime resolution, and optional authentication                       |
| `anvil-protocol`                                                      | Player registration, version selection, authentication compatibility, composition, and observations                                                                                 |
| `anvil-protocol/protocol-mcprotocol`                                  | Wiring bundle publishing the MCProtocolLib library: shaded `mcprotocol-common`, the client port and the client segments; owns `mcprotocol-releases.toml` and `pinLibraryReleases`  |
| `anvil-protocol/protocol-mcprotocol/mcprotocol-api`                   | Release-neutral MCProtocolLib client port that client segments implement for the worker shell                                                                                       |
| `anvil-protocol/protocol-mcprotocol/mcprotocol-common`                | MCProtocolLib library provider, release data, worker host and segment selection, MCProtocolLib-free worker shell, and private authentication store                                  |
| `anvil-protocol/protocol-mcprotocol/mcprotocol-client`                | Source-free side folder exporting one client segment per MCProtocolLib release whose client code differs                                                                            |
| `anvil-protocol/protocol-mcprotocol/mcprotocol-client/V*`             | Client segments `V1_18_2`, `V1_21_1` and `V1_21_11`, each implementing only `McProtocolClient` from its release key on; `V1_21_11` also serves `26.1.2`                             |
| `anvil-platform/platform-api`                                         | Platform-provider/planning SPI, version data contract, artifact sources, distribution validation, and configuration contracts                                                      |
| `anvil-platform/platform-planning`                                    | Platform declaration validation, version data reading, effective Java/topology requirements, forwarding negotiation, artifact planning, and provider preparation/configuration      |
| `anvil-platform/platform-*`                                           | Provider-specific distribution/configuration implementations and platform-agent assemblies                                                                                          |
| `anvil-agent/agent-api`                                               | Shared operation descriptors, payloads, identities, types, and exceptions; no client/server contracts or core dependency                                                            |
| `anvil-agent/agent-client/client-api`                                 | Host-side clients, directories, connections, and artifact lookup; exports shared agent contracts without core or capability APIs                                                    |
| `anvil-agent/agent-server/agent-server-api`                           | Embedded endpoint, native service, and operation provider contracts; exports only shared agent contracts                                                                            |
| `anvil-agent/agent-client`                                            | Host connections, stable process clients/directories, agent sessions, player observations, and artifact location                                                                    |
| `anvil-agent/agent-server`                                            | Embedded authenticated endpoint, native operation dispatch, extension loading, and the shaded agent artifact used by platform agents                                         |
| `anvil-integration/integration-junit`                                             | JUnit annotations, context injection, and lifecycle integration                                                                                                                     |
| `anvil-integration/integration-gradle/gradle-junit`                                      | Optional anvilTest task and JUnit dependency wiring                                                                                                                                 |
| `anvil-tooling/tooling-api`                                           | Editor-independent scenario, session, target, and log contracts                                                                                                                     |
| `anvil-integration/integration-gradle/gradle-tooling`                             | Gradle task discovery, definition indexing, source-set preparation, and tooling runtime                                                                                             |
| `anvil-tooling/tooling-runner`                                        | Reusable sessions, direct scenario-definition discovery, terminal commands, and structured protocol; supplied engine factory, no default launcher dependency                      |
| `anvil-integration/integration-intellij/intellij-api`                                      | IntelliJ integration contracts and immutable cross-module values                                                                 |
| `anvil-integration/integration-intellij/intellij-engine`                                   | IntelliJ integration lifecycle, tooling processes, project services, and account logic                              |
| `anvil-integration/integration-intellij/intellij-gradle`                                   | Optional native Gradle project discovery, sync, and preparation adapter                                |
| `anvil-integration/integration-intellij/intellij-ui`                                       | IntelliJ Swing, tool windows, dialogs, settings, navigation, and presentation                                    |
| `anvil-integration/integration-intellij/intellij`                                          | Plugin descriptor, branding, dependency composition, and IDE packaging                                            |
| `anvil-integration/integration-gradle/gradle-plugin`                                      | Standard scenario entry point assembling shared Gradle declarations, foreground tasks, and project discovery                                                                                         |
| `anvil-integration/integration-gradle/gradle-platforms`                                    | Gradle adapters for platform providers                                                                                                                     |
| `anvil-integration/integration-gradle/gradle-capabilities`                                 | Gradle adapters for capability providers                                                                                                                   |
| `anvil-testkit/tests`                                                 | Cross-module runtime and live assertions                                                                                                                                            |
| `anvil-testkit/fixtures`                                              | Independent consumer build for process, server-plugin, and extension fixture JARs                                                                                                   |
| `anvil-testkit/support`                                               | Host-side fixture artifact access and scoped extension loading                                                                                                                      |
| `examples/proof-of-patience`                                          | Standalone consumer example, not a home for framework system assertions                                                                                                             |
| `build-logic`                                                         | Shared conventions grouped by `module/`, `packaging/`, `adapter/`, `library/`, `platform/`, `capability/`, `integration/`, and `fixture/`                                           |
| `build-logic/settings`                                                | Lean settings conventions loaded by every project's parent classloader: library registry, library repositories, and library and platform layout checks                             |
| `docs/content`                                                        | Task-oriented Scriptorium guides for using, extending, and contributing to Anvil                                                                                                    |
| `scriptorium.project.json`                                            | Scriptorium project metadata and version policy                                                                                                                                     |

## Dependency and extension rules

- `anvil-api` has no Anvil project dependency. `anvil-engine` depends only on `anvil-api`.
  Ordinary implementations and API modules consume only their own family's APIs and `anvil-api`;
  foreign API dependencies and re-exports are forbidden unless an ancestor-owned contract API is
  explicitly declared through `architecture.sharedApis`. Each exposed API needs permission independently.
  Organizational grouping does not merge families. Agent client/server families share only `agent-api`;
  they must not depend on or re-export each other's API or implementation.
- Keep concrete platform SDKs, MCProtocolLib, serialization, transport implementations, and assembly
  modules out of `anvil-api`. Global declarations and engine/scenario registration belong in core;
  scoped services stay in their owning APIs even when another family needs them.
- Bind scoped services at the assembly boundary through consumer-owned inputs or preparation contracts.
  Keep those contracts focused on the required operation; do not copy a complete foreign API into
  a wrapper or pass Anvil family services through untyped service lookup.
- Implementations/adapters consume APIs and external libraries, not sibling implementations.
  Do not disguise source dependencies as external Maven coordinates or classify implementations as
  assemblies to evade verification. The launcher owns internal composition and packaging;
  `anvil-integration` is reserved for external integrations.
- Use `ServiceLoader` for the existing provider SPIs. Preserve service descriptors and merge them
  when shading. Do not add broad reflection scanning as an alternative discovery path.
- Each player selects its protocol library: its own `protocolLibrary`, else the scenario's, else the engine's,
  else the one installed library whose release supports its Minecraft version most strongly; a tie requires an
  explicit choice. A player created from a lease uses the leased account's library instead. Launcher composition
  creates one capability runtime per selected library.
- Public capabilities extend `PlayerCapability` or `ProcessCapability`, both rooted in the neutral
  `Capability` contract. Dependencies remain within the same owner. Neutral player providers use
  shared observations and dependencies. Protocol-backed providers declare library support through
  `supportedLibraries()`; agent process providers select supported platform IDs.
- Retrieve only declared capability dependencies. The agent-backed `Server` capability must remain
  independent of `Session`.
- Packet behavior belongs in feature-owned native implementations. Feature wiring registers scoped
  typed operation/event descriptors and `WorkerExtension<S>` bindings, where `S` is the session type the
  extension accepts: built-ins accept `Object` and let the port's `sessionType()` cast it, because the
  native session type differs between releases. Release-specific packet code lives in segments behind the
  feature's port.
  Shared `OperationRegistry` accepts typed handlers. `WorkerCapabilities` enforces registration phase,
  namespaced uniqueness, and cleanup; launcher `ProtocolOperationRegistry` binds handlers to
  protocol-owned native operations; capability codecs own typed handler wire encoding/decoding.
  Never add feature branches or an opaque universal packet abstraction to the MCProtocol worker.
- A built-in capability is a family folder whose root applies the `module-capability` convention and only wires
  its members into `builtin-<feature>`: `<feature>-api` (artifact `builtin-<feature>-api`) holds the API and,
  for packet capabilities, the stateless port `<feature root>.packet.<Feature>Packets<S>`; `<feature>-common`
  (embedded, unpublished) holds the host provider and the library-neutral `<Feature>Binding<S>`;
  `<feature>-<library>` (artifact `builtin-<feature>-<library>`) holds the library's `WorkerExtension<Object>`
  and its `V*` segments, which implement only the port and never wire anything. The root is a wiring bundle: consumers compile against
  its shaded JAR, and its sources and Javadoc JARs carry the embedded common code. The convention derives every
  artifact ID from the folder names and fails a build file that sets its own, and `gradle-capabilities` tests
  that each family has a plugin installing `builtin-<feature>`.
  Common code lives in the feature packages, never in `common` or `util` packages. A process family without
  common code or library sides, such as console, keeps its agent-backed provider in the root: the provider
  binds the family API to agent operations, which only an assembly may depend on; the convention refuses root
  code in every other family. The extension takes the port's adapter only through
  `PlayerBindingContext.adapter(Class)` while binding a player, never in its constructor and never through
  `ServiceLoader`; `checkAdapterLookup` fails a library side whose classes reference `ServiceLoader`. No segment
  for the release, a segment failing its linkage self-check, or more than one adapter throws
  `AdapterUnavailableException`, and the worker reports the capability unavailable with that reason; a
  `LinkageError` or `ServiceConfigurationError` while binding likewise disables only that capability.
  For a player with a native worker, composition skips every protocol-backed provider whose capability the
  worker does not install, and its dependents, with the worker's reason or "not installed by the <library>
  worker"; host providers do not check installation themselves. Players without a native worker compose
  every provider of their library.
- `PlayerBindingContext` supplies an existing player's native SDK/session, lifecycle and release adapters to a
  binding; it is not another player entity. Its `adapter` maps to the native worker's `NativePlayer.adapter`.
  `PlayerConnectionEvent` lives under `.api.model.player`, while
  `ViewRotation` and `EventDescriptor<E>` live directly under `.api.model`. View rotation holds yaw/pitch;
  an event descriptor pairs an event ID with its payload class.
  Event payloads such as `PlayerConnectionEvent` remain separate values. Worker bindings own per-player
  state and native listeners. Use `bindNativeSession` for generation-scoped listeners across reconnects;
  old native handles must not silently bind to replacement sessions.
  Neutral player provider/context and lifecycle contracts live under `capability.api.player`.
  Protocol factories, channels/events, and native worker contracts live under
  `capability.protocol.api.player` in `capability-protocol-api`. Agent-backed player
  factories implement `AgentPlayerCapabilityProvider` with `AgentPlayerCapabilityContext`.
  Agent-backed process factories implement `AgentProcessCapabilityProvider` with `AgentProcessCapabilityContext`; these have
  process/platform identity and a typed request channel, without player state. Both provider kinds
  live in `capability-agent-api`, under `.api.player` and `.api.process`, return shared
  `CapabilityDescriptor` values, and use inherited dependency/cleanup contexts. Both mechanism APIs
  depend only on the shared capability API; player contexts extend its neutral player context.
  All matching player provider kinds form one dependency graph before capability creation.
  Agent player factories select a channel by process name; process factories
  use their own channel. Shared composition owns dependency ordering and cleanup, while launcher
  adapters bind request channels to agent clients. Capability APIs do not expose agent-family types.
- `RunningProcess` extends `CapabilityOwner<ProcessCapability>` and exposes capabilities directly.
  The logical process owns capability instances across JVM generations; global process contracts do
  not depend on agent APIs. Agent-backed implementations are created after initial transport readiness
  and close after players, before process/transport finalization. A restart replaces the transport
  while retaining capability instances; requests can be unavailable during replacement. Closed owners
  reject capability lookup and report `hasCapability` as false.
- A protocol library may expose its own stable services through `ProtocolPlayer.findService`.
  Anvil protocol/capability services cross their boundary through explicit assembly bridges, not
  `findService` or a fake universal packet abstraction.
- Authentication is an optional `ProtocolLibraryProvider` service. Tooling depends on that API, not
  MCProtocol's account-store implementation. Account files are managed outside project configuration.
  The IntelliJ account manager and Gradle `anvilAccount` share the `AnvilAuthentication` entry point.
  Named pools live in the account directory's versioned `pools.properties`, documented on
  `AccountManager.pool(String)`; the runtime reads it and the IDE writes it, with the same rules.
- External embedded-agent handlers implement `agent.server.api.operation.AgentOperationProvider`
  and own their operation namespace.
  Install their JARs under `plugins/anvil-agent-extensions`; do not bundle Anvil agent APIs or
  platform SDKs into those JARs. Host capabilities receive scoped request channels; launcher adapters
  bind them to connections obtained through `AgentDirectory`.
- Platform agents expose native services and their scheduling rules. The endpoint owns its extension
  class loader; the scenario owns host connections.
- `executionProviderId` selects providers in declarations and execution plans; `RunningProcess.executionId()`
  identifies one execution attempt. Engine `ProcessTimeouts` defaults are overridden independently by scenario
  startup/shutdown values. Engine `ProcessScheduling` governs each scenario operation, not aggregate engine usage.
  Keep artifact download concurrency separate.
- `JavaSelection` groups Java requirement and source in engine, scenario, and process declarations.
  Omitted members inherit independently; an explicit empty requirement overrides an inherited version
  and selects the platform's preferred LTS. `JavaRequirementPlanner` supplies execution a selection with
  an exact feature version. Scenario listener binding and LAN permission belong to `NetworkPolicy`,
  alongside exposure and access policy.
- Processes run on LTS releases only (11, 17, 21, 25, then every fourth); Java 8 is not one of them.
  Each provider owns `<platform>-versions.toml` (Java rows, `[agent] minimumJava`, verified and known
  versions) and exposes it through `PlatformProvider.versionData()`; platform-planning reads it, so
  platform-api carries no parser. The agent minimum raises the effective minimum and the default LTS;
  a default it pushes above the row maximum is refused. A maximum is refused unless the row names a
  bypass property; planning then adds `-D<property>=true` before provider defaults for explicit requests
  only, never for the default. Version and Java assessments follow `SupportLevel`/`SupportPolicy` and
  refuse before any download. Execution resolves exactly the planned feature version; never fall back
  to a newer or older JVM.
- Providers that install an agent apply the `module-platform-provider` convention and name the agent through
  `platformAgent(...)`; `checkAgentJava` fails unless the declared `[agent] minimumJava` equals the Java
  release the agent targets and no agent class, embedded code included, needs newer Java. The `build-platforms`
  settings plugin fails the configuration of any project whose main resources ship version data with an
  `[agent]` table unless it applies the convention. The `platformAgent` configuration only feeds that check, so
  it is intentionally outside the architecture verifier's production configurations even though it names
  another family's agent. VERIFIED means run by the live matrix: a platform's and a protocol library's
  verified data list exactly the combinations `CompatibilityScenarioFactory` runs (checked in every build by
  `CompatibilityScenarioFactoryTest` through the server suite's `matrixTest`), and every other supported
  version is only known (COMPATIBLE). Docs keep one Java-per-version table in the Java provisioning guide,
  which `JavaVersionTableDocumentationTest` compares with the installed providers' version data and the
  planner's defaults.
- Java requirements are execution-agnostic. Local homes and verified archives belong to Java provisioning;
  Docker image mappings belong to the Docker execution provider.
- Execution providers own network topology and endpoint translation. A local loopback bind is host exposure
  control, not process isolation; strict proxy-only isolation is rejected unless the provider can enforce it.
- A simulated player's name is its unique identity within the scenario; its Minecraft username defaults to
  the name and may be shared by several players. Player observations follow the player's own connection from
  the process it connects to and never look a username up across every agent. Routes name scenario processes.
- `api.process.RunningProcess` exposes lifecycle, address, and console; server/proxy specializations live in
  `api.process.type`. Player identities/routes belong to
  `PlayerObservation` and the Server capability.
- Forwarding is negotiated from provider-supported modes before launch, not from platform-name
  branches in the engine. Platform configuration writers own their formats and preserve unrelated
  values while replacing runtime-owned settings structurally.
- Use the artifact registry and exact classpath JARs; never guess another module's `build/libs` output.
- Public API identities stay separate from launcher internals. Publish each shaded artifact once,
  and keep plain/shaded output filenames distinct.

## Java and package conventions

- Target Java 21. Use tabs for indentation and spaces only for alignment.
- Use imports instead of fully qualified type references unless qualification resolves a name collision.
  Keep runtime class-name strings and service-descriptor names fully qualified.
- Use feature-oriented packages: contracts at the feature root, public reusable
  values under `model`, and public enums/closed value types under `type`.
- Physical paths must match package declarations. Keep API models as separate top-level files.
  Use private inner types for implementation-local state; records are allowed only as small inner records.
- Add subpackages for coherent responsibilities shared by several files, not merely to classify
  one implementation; a package holds at least two files. The fixed role packages `model`, `type`,
  `exception`, and `packet` are exempt: every packet capability keeps its release port in `.packet`,
  even when it is the package's only file. A segment's version package (`.v1_18_2`) and a library side's
  wiring package (`<feature>.mcprotocol`) hold one class by design and are exempt too. Avoid catch-all
  `util` or `common` packages.
- Keep classes and methods focused. Prefer early returns and `continue` to nested or `else if` chains.
  For a short single-statement `if`/`for`, omit braces.
- Prefer try-with-resources for lexical ownership. When cleanup must continue across independent
  resources, use a focused helper to preserve the first failure and suppress later cleanup failures;
  do not repeat identical try/catch blocks for each resource.
- Prefer Lombok for immutable values, constructors, getters, and builders. Prefer final fields;
  add mutation only where lifecycle integration requires it. Do not add empty default constructors.
- For long constructor parameter lists, put each parameter on its own line and the closing
  parenthesis on its own line.
- Use JetBrains `@NotNull`/`@Nullable` consistently. Guard actual input/failure boundaries; do not
  add speculative null, blank, or empty checks throughout internal flows.
- Public API classes/interfaces/enums and public methods need useful multiline Javadocs.
  Include parameter/return details for nontrivial contracts. Code examples use
  `<pre>{@code ...}</pre>`; do not use single-line Javadocs or `@snippet`.

## Gradle conventions

- Use Kotlin DSL and keep versions in the purpose-grouped `gradle/libs.versions.toml`.
- Shared behavior belongs in convention plugins under `build-logic`.
- Project paths mirror directories. Top-level product modules use `anvil-`; nested names follow
  their family. Keep stable consumer artifact IDs and group `me.whereareiam.anvil`.
- Order dependency groups: `api`, `implementation`, `compileOnly`, `embedded`, `runtimeOnly`,
  `annotationProcessor`, then test configurations. Put project dependencies before external ones,
  sort within groups, and separate nonempty groups with a blank line.
- Plugin blocks put built-ins first, catalog aliases second, local conventions last.
- Consumer live scenarios and journeys live in `src/anvil`; ordinary plugin tests remain in `src/test`.
- Examples are standalone consumer builds, excluded from root project discovery. Publish Anvil to
  Maven Local first, then run `./gradlew -p examples/proof-of-patience build anvilTest` with the same version.
- Preserve configuration-cache behavior when changing task inputs or plugin wiring.
- Keep platform and capability units explicit. `me.whereareiam.anvil` supplies scenarios and project
  discovery; `me.whereareiam.anvil.junit` supplies automated testing. Both use the shared Gradle base;
  neither installs default capabilities or depends on the other execution plugin.
- EULA acceptance uses `anvil { acceptEula() }`.

## Runtime and reproducibility requirements

- Validate scenarios, EULA, distribution selectors, native versions, capabilities, authentication,
  Java, and forwarding before launching processes.
- Automated distributions pin a build or content checksum. `latest` is manual-only.
  Spigot uses GetBukkit prebuilt JARs with `Distribution.pinned(version, sha256)`; do not restore
  BuildTools execution or silently replace a pin.
- NeoForge selects `Distribution.remote(minecraftVersion, neoForgeRelease)`; the provider refuses a release of
  another Minecraft version. Its installer is verified against the NeoForged Maven checksum or an explicit pin,
  runs once per release into the cache, and publishes the installed server by an atomic move. Workspaces link
  the installed files and start through the installer's server starter JAR. NeoForge has no identity
  forwarding, so its only forwarding mode is `NONE` and planning refuses it behind a proxy.
- The NeoForge agent is a mod compiled with ModDevGradle against the oldest NeoForge release in
  `neoforge-versions.toml`; it uses only names that later releases keep, so one JAR loads on every supported
  release. NeoForge servers load no Bukkit plugin: their live scenarios carry no fixture, and
  `NeoForgeServerSystemTest` drives them with vanilla commands.
- Local/named server artifacts declare `minecraftVersion`. Native clients must match every reachable
  server. Preserve explicit client overrides and deterministic native selection; no implicit ViaVersion fallback.
- Prepare declared assets and caches before provider configuration. Runtime ports, forwarding,
  agent credentials, and EULA values take precedence. Independent preparation and startup work may run
  concurrently within configured limits. Configure each process during its start, including the first
  start and every restart; independent configurations may overlap and must use the supplied process
  workspace and planned shared values. Preparation owns reusable inputs, not launch resources.
- Agents bind to loopback and authenticate with per-run random tokens. Non-loopback game listeners
  require a manual scenario and explicit LAN opt-in.
- Global `ScenarioFactory.create` acquires a context without starting processes. The core engine builder
  requires its factory at construction; extensions register only lifecycle contributions and resource ownership.
  Launcher assembly creates the factory and transfers shared services before installing caller extensions. There is one
  lifecycle: prepare, `ScenarioContext.start`, then `finish(boolean)`. The engine's `start` method
  performs those first two phases; global extensions and setup run once after full scoped readiness.
  Do not retain separate ready-only executors, optional preparation adapters, or fallback startup paths.
  Each process execution has an opaque UUID assigned by execution. Consumers correlate that identity across snapshots and logs; it is not a restart counter.
  Use `ScenarioContext.finish(boolean)` and `ScenarioAttachment.finish(boolean)` to propagate caller
  outcomes; default `close()` means normal completion and does not erase earlier lifecycle failures.
- A process declared with `ProcessLifetime.ENGINE` belongs to the launcher's `RetainedProcesses`, not to its
  scenario's process group: a scenario's kept processes are planned and run as a group of their own, and
  `ProcessLease` lends one running set to one scenario at a time. `ScenarioFactory.create` may start that set,
  because the scenario's own processes are prepared against its addresses and adopt its forwarding settings.
  A successful scenario returns the set; a failed one stops it and keeps its workspaces. Do not add scenario
  state to a retained set, and do not share one between scenarios that run at once.
- The engine prepares and starts scenarios concurrently; shared services reached from `ScenarioFactory.create`
  or a start must be safe for that. JUnit uses one engine for the whole test run.
- Start servers before proxies; stop processes in reverse dependency order. Cleanup attempts every resource,
  preserves failures, and retains diagnostic workspaces and bounded output tails when a run fails.
- Never put online access/refresh tokens in Gradle inputs, CLI arguments, environment variables,
  system properties, logs, or project workspaces. Workers receive them only through private stdin.
  Agent session tokens are separate per-run credentials used by the managed child process.
- Use `AnvilScenarioDefinition` as the one-scenario unit for JUnit, foreground execution, and IDE
  discovery. Gradle indexes compiled definition classes; scenario metadata supplies presentation labels,
  categories, and tags. Keep generated matrices as explicit definition classes or an advanced source,
  rather than requiring a catalog registry.
- Presentation metadata remains optional. Resolve labels in tooling while preserving technical
  scenario-definition, process, capability, and player identities.
- `RunnerSession` owns its engine and foreground context. Partial execution uses the canonical
  preparation/start lifecycle; process allocation and generations remain in managed execution, with scoped
  services bound by the launcher. Failed runner operations finalize contexts with `finish(false)`.
- IntelliJ scenario-source discovery uses `BuildIntegration` through
  `ProjectBuildIntegrations`. Keep Gradle APIs in the optional
  `integration-intellij/intellij-gradle` adapter. Import the native Gradle task model during sync and
  use the standard preparation task as the source marker; do not scan source directories or
  instantiate scenario definitions. Project import does not compile sources or resolve artifacts.
- Build integrations own compilation, fixture/artifact resolution, and preparation. Consumer and
  framework targets use the standard Anvil plugin and its internal preparation task. The generated launch manifest contains
  explicit inputs and belongs to the caller; the IDE removes it after launch or failure. Native
  authentication credentials do not belong in manifests. Version the local session protocol.
- The main BOM aligns the project-tooling API, Gradle model API, artifact binding, and Gradle producer
  publications directly. Derive constraints from actual publications; do not maintain a second tooling BOM.
- Tooling capability features use the public tooling-extension-api. Keep built-in capability imports out of
  the generic runner and IDE. The optional tooling-builtin module contributes through the same ServiceLoader
  SPI as external projects. Wire models remain independent of core/runtime APIs. Validate invocation session,
  target, capability support, availability, and inputs before calling a handler. Never infer arbitrary capability
  methods through reflection. Observations are read-only contributions; their failures remain visible as values.
- Keep IDE views and controls independent of engine implementations. Saved selections use stable
  project/definition/scenario identities; optional names never change execution routing. Native
  settings control personal IDE behavior, while runtime options stay in project/scenario definitions.
- Adding a Minecraft version follows the contributing guide `docs/content/contributing/minecraft-versions`.
  Add the version to the `mcprotocol-releases.toml` release that speaks its protocol, or a `[[release]]` row
  for a new protocol (version, module, protocol, Minecraft versions, verified versions, Java, features), and
  pin its closure with `pinLibraryReleases`. Add a segment only to a side whose newest segment fails
  `checkSegmentLinkage` against the new closure, named after the new release key; a segment folder must name
  a release key, so raising a key renames that segment. Add the release to `McProtocolWorkerContractTest`,
  platform `known` versions and `[[java]]` rows only where Java changes, and mark as verified exactly what
  `CompatibilityScenarioFactory` runs; every other listed version is COMPATIBLE, and user-supplied release
  data is UNTESTED. Update the versions page, the Java table, the README, and the FAQ. The worker shell never
  imports MCProtocolLib; release-specific client code lives only in `mcprotocol-client` segments, whose
  `linkage.txt` requirement keywords the worker's self-check enforces. A release with an unpinned closure is
  listed but cannot be launched: preparing a scenario whose servers would use it by default prints a warning,
  and creating a player that selects it is refused.

## Tests and verification

- Unit and focused integration tests stay in their owning module's `src/test`, normally mirroring
  the production package. Test observable contracts rather than duplicating implementation details.
- IntelliJ tests belong to `intellij-engine`, `intellij-ui`, or `intellij-gradle` according to
  the behavior under test. API contract tests live in `intellij-engine`; `intellij-api` has no test sources. Only production descriptor/composition smoke tests belong
  in `intellij`. Engine tests use recording output without a UI dependency; UI tests bind their
  collaborators locally without depending on the assembly. Share controlled process fixtures through
  the engine's `src/testFixtures`, never through production artifacts or another module's whole test tree.
- `anvil-testkit/tests/runtime/src/test`: cross-module discovery/composition without live Minecraft.
- `anvil-testkit/tests/server/src/test`: real sessions, capabilities, routes, and external agents,
  grouped by behavior. The whole task requires `-Panvil.testMode=full`; only its `matrixTest` task, which
  checks `CompatibilityScenarioFactoryTest` without starting a server, runs in every build.
- The independent `anvil-test-fixtures` consumer build under `anvil-testkit/fixtures` contains
  `test-process`, `test-server-plugin`, and `test-extension`. They use `src/main`; dependencies on Anvil
  use public artifact aliases, with current source projects substituted in the root composite build.
  Gradle produces fixture variants and supplies their exact artifacts to tests. Keep JAR construction
  out of Java test helpers; `TestExtensionLoader` owns discovery isolation and classloader cleanup.
  Shared host helpers belong in `anvil-testkit/support`. Share fixtures only when reused or required
  by a packaging boundary.
- After publication, run `./gradlew -p anvil-testkit/fixtures clean build -PanvilVersion=<version>`
  to verify the public artifacts without source substitution, in addition to the standalone example.
- Do not mix live and non-live methods behind tags in one test class. Do not move framework assertions
  into the consumer example. Never introduce real online-account tests into CI.

Run the nearest relevant test first, then architecture/build checks:

~~~shell
./gradlew verifyArchitecture
./gradlew checkSegmentLinkage
./gradlew :anvil-engine:test
./gradlew :anvil-environment:execution:execution-managed:test
./gradlew :anvil-platform:platform-planning:test
./gradlew :anvil-protocol:test
./gradlew :anvil-agent:agent-client:test
./gradlew :anvil-agent:agent-server:test
./gradlew :anvil-capability:test
./gradlew :anvil-protocol:protocol-mcprotocol:mcprotocol-common:test
./gradlew :anvil-integration:integration-gradle:gradle-plugin:test :anvil-integration:integration-gradle:gradle-capabilities:test :anvil-integration:integration-gradle:gradle-platforms:test
./gradlew :anvil-testkit:tests:runtime:test
./gradlew :anvil-testkit:tests:server:matrixTest
./gradlew :build-logic:check :build-logic-settings:check
./gradlew build
~~~

For protocol behavior, run the exact worker contracts, which cover every release in
`mcprotocol-releases.toml`, and full live coverage. For kicking/reconnects/identities, include
`PlayerIdentityReconnectSystemTest` for both Paper versions it runs. For platform providers/forwarding, run
every affected direct/proxy combination. Inspect retained `anvil-console.log` files after startup or routing
failures.

~~~shell
./gradlew test -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
~~~

## Documentation and delivery

- Scriptorium uses the consumer contract: `scriptorium.project.json`, `docs/content/**`,
  and `docs/assets/**`. Keep Markdown/MDX frontmatter (`title`, `description`) and ordered
  `meta.json` navigation. Do not recreate loose developer notes under `docs/`.
- Documentation describes current behavior and clearly labels examples and limitations. Organize
  navigation by task: getting started, writing tests, running environments, extending Anvil, and
  contributing. All readers are developers; do not recreate a generic developer section. Public
  extension guides belong under `extending`; repository-maintenance guides belong under `contributing`.
- Update relevant pages and README links when changing public DSL, tasks, versions, authentication,
  cache layout, packaging, test commands, or extension behavior. Keep examples copyable and verify symbols.
- Use relative content links. Validate navigation, local links, and Scriptorium content
  compilation after documentation changes. Generated `.scriptorium/` output is ignored.
- Keep `LICENSE`, wrapper, POM metadata, and CI/release workflows valid. Entry workflows only select a trigger
  and call `reusable-verify.yml`, `reusable-live.yml` and `reusable-publish.yml`; shared steps come from `whereareiam/devops`, and
  only Anvil-specific ones live in `.github/actions`. Pull requests automatically get the metadata check and
  the quick `build` checks, which start no server. Full verification with the IntelliJ plugin, standalone
  consumers and real platforms is maintainer-requested through `workflow_dispatch` and reports a
  `Full verification` status on the pull request's head commit. Development builds are manual-only and
  publish a branch-qualified version. Release Drafter updates on `dev` pushes or manual dispatch,
  using `feature`, `change`, `bug`, `dependencies`, `major`, and `skip-changelog` labels. Published
  releases trigger full verification, Maven publication and IntelliJ Marketplace publication. No scheduled
  nightly workflow is required. A clean machine runs `publishToMavenLocal -Panvil.bootstrap` before any other
  task: the bootstrap build leaves out the server suite, which applies the plugin at the build's own version.

## Integration composition

- External integrations live under `anvil-integration`; editor-independent session tooling stays in `anvil-tooling`.
- Gradle base owns the public `AnvilExtension` surface and creates it with the `anvil` source set. Gradle
  execution adapters consume its source set, versioned module coordinates, EULA acceptance, and tracked
  artifact registrations through `getByType(AnvilExtension)`; do not restore a private state holder or lookup.
  `gradle-dsl` only maps that configuration to engine properties. Scenario discovery scans the `anvil`
  source set only; ordinary `src/test` classes are never compiled or scanned for definitions.
- JUnit runtime does not depend on Gradle or IDE code. Gradle–JUnit assembly installs its runtime dependency
  and configures Gradle Test tasks without importing the scenario plugin or project-discovery producer.
- Gradle project tooling lives in the normal root modules under `anvil-integration/integration-gradle`: `gradle-tooling`
  and `gradle-artifacts`. The standard Anvil plugin wires the preparation task internally; JUnit wiring does
  not depend on the project producer.
- The IntelliJ integration keeps contracts in `intellij-api`, lifecycle and tooling logic in
  `intellij-engine`, native Gradle behavior in `intellij-gradle`, IntelliJ presentation in `intellij-ui`,
  and service-interface bindings, extension registration, and plugin packaging in `intellij`.
  UI areas live under `view.settings` and `view.window.main` / `view.window.account`, with components
  scoped beneath their consumers. Shared console presentation lives in `component.console`; native
  Run/Debug configuration integration lives in `runconfiguration`. Its project-scoped
  `RunConfigurationService` owns saved-configuration matching, source resolution, validation, and
  launch requests through `EnvironmentLifecycle`. The assembly registers the service; the catalog
  calls it to save configurations, the editor uses its source choices, and `AnvilRunConfiguration`
  adapts native validation/execution while retaining XML persistence and giving the Run tab its own console presentation. Ordinary
  catalog runs remain independent of saved configurations. Main-window navigation lives in
  `view.window.main.navigation`: `DefinitionNavigator` owns indexed source lookup and editor navigation,
  while `WorkspaceNavigator` refreshes and opens process directories. Lookup carriers stay private.
  `MainWindowController` owns tool-window contents and retention. Catalog layout belongs to
  `ScenarioCatalogPanel` composes the catalog screen through `CatalogView`; `CatalogController` composes three concrete owners in the same package.
  `CatalogDiscoveryController` observes discovery/catalog/preferences and owns source controls and
  loading/failure messages. `CatalogExecutionController` replaces the active-session subscription and
  renders overlays/inspector state. `CatalogCommandController` rechecks selection and availability for
  launch/save/navigation requests. Each owner disposes its own observers; closing a catalog does not
  stop its environment. Command failures are reported to the discovery owner for display. Its tree lives under `view.window.main.catalog.tree`: `ScenarioTreeView` owns the
  Swing tree boundary, `ScenarioTreeModel` projects typed scenario/process nodes, `ScenarioTreeExpansion`
  retains stable user expansion choices, and `ScenarioTreeRenderer` owns labels, icons, tooltips, and
  execution overlays.
  `SettingsForm` owns editable controls, while `AnvilSettingsConfigurable` handles Apply/Reset.
  Define all panels and dialogs in `intellij-ui/src/main/kotlin`, using Kotlin UI DSL for forms and Swing
  composition for tool-window layouts, alongside Java
  controllers and platform adapters in the matching `src/main/java` packages. Dialogs own titles,
  actions, component composition, and disposal; Java controllers own persistence, service calls,
  asynchronous work, validation rules, and operation lifetimes. Forms own layout,
  editable values, presentation validation, and local control interactions. Java owns service calls,
  persistence, subscriptions, history policy, and operation lifetimes. Use ordinary methods and
  Java callback types at that boundary. Custom tree/list renderers and platform console adapters may
  remain Java; panel construction belongs in Kotlin. `EnvironmentSessionController` owns session
  subscriptions and disposal; `EnvironmentController`, `PlayersController`, `ConsoleController`, and
  `TargetContributionsController` own their screen interactions. Configure Kotlin's Lombok compiler plugin
  alongside Java annotation processing so Kotlin can consume generated Java members in the UI module.
  Kotlin compilation targets Java 21 and the baseline IDE's Kotlin API; use the IDE-provided runtime.
  UI and Gradle production code depend on `intellij-api`, never engine implementations. Persistence
  beans stay in engine; cross-module settings use immutable snapshots. Native IntelliJ project and
  disposal types are allowed in IntelliJ contracts; Gradle SDK types stay in the Gradle adapter.
- IntelliJ environment presentation packages use `environment`; reserve the repository's `/run/`
  directory for generated runtime files. Engine features live under `source`, `scenario`, `settings`,
  `account`, and `tooling`. Process transport/termination belongs to `ToolingConnection`.
  `ProjectSourceDiscovery` implements `SourceDiscovery` and owns source selection, sync,
  and scenario loading; `ImportRefreshPolicy` decides when a completed import reloads the selected source. Once initialized, it observes the project independently of open views;
  `DiscoverySnapshot` and `DiscoveryState` expose its state. Refresh waits for active environment cleanup.
  `ProjectScenarioCatalog` owns catalog state, discovered definitions, and discovery output;
  `ProjectEnvironmentLifecycle` owns the active environment and retained handles. `ProjectToolingHost`
  arbitrates reuse/reservation/replacement of one tooling launch, and `ToolingLaunch` owns preparation,
  the runner, temporary credentials, and cleanup. Its private `LaunchResources` owns partially acquired
  files and accounts; try-with-resources releases both even when another cleanup step fails.
  `ToolingConnection` owns stdout/stderr reading and drains diagnostic delivery before file cleanup.
  `ToolingLaunch` publishes one typed `ToolingLaunch.Outcome` (finished, stopped, or failed) after cleanup, and requests scenario discovery only when asked; listener failures cannot skip resource
  release or pending-future completion. A replacement waits for the previous launch's cleanup.
  `EnvironmentExecution` owns one environment snapshot and lifecycle. `RetainedEnvironmentSession`
  stays bound to that execution; commands never look up a replacement through a project service.
  A disposed handle is removed from the retained list while its launch remains reserved until cleanup.
  `StoredSessionLog` lives in the shared `log` package. `CatalogSnapshot` and its `CatalogState` describe catalog
  lifecycle, while the engine calculates `EnvironmentState` from `SessionSnapshot` and exposes it
  through `EnvironmentSession`; UI derives labels and tones from those typed states.
  UI console presentations subscribe without an engine-to-UI factory. `ToolingClient` owns handshake,
  ordered request submission, response correlation, and pending-result completion on closure or failure.
  `ToolingMessageCodec` owns typed JSON binding and version checks; transport frames stay internal.
  Portable `tooling-api` payloads use Jackson-compatible Lombok builders and are reused by both peers;
  do not recreate their fields in a manual JSON model reader. `ScenarioOperations`, `EnvironmentOperations`,
  and `ProcessOperations` declare predefined `ToolingOperation<Q, R>` schemas at the `tooling-api` root.
  `ToolingClient.request` retains the declared response type, including collection element types.
  `ToolingOperationRegistry` binds typed runner handlers and cancellation policy; `ToolingRequestReader`
  decodes registered payload types generically. Do not add per-operation codec factories or duplicate
  operation enums. `ToolingProtocolWriter` emits typed envelopes. Protocol 7 uses nested action targets.
  Environment contracts expose explicit controls, never string protocol operation names. Response IDs
  correlate requests and must not be overloaded with operation names.
  `ConfiguredAccountLibrary` owns the project-facing account contract while
  `AccountDirectoryRepository` and `AccountPoolRepository` own persistent files.
  `AccountWorkspace` owns temporary credential copies and cleanup. Keep native persistent component
  names stable when renaming implementation classes.
- IntelliJ is a frontend in the logical `:anvil-tooling` API family, declared through `architecture.family`
  on `integration-intellij`. Its physical location remains under `anvil-integration`. Reuse portable
  models from `anvil-tooling/tooling-api` through `intellij-api`; keep IDE-specific contracts there.
  Ordinary implementation dependencies remain forbidden. The IDE launches the tooling runtime in
  a separate project JVM and does not depend on the runner implementation.

- `tooling-runner` is reusable implementation code over core/tooling APIs. `AnvilRunner` and
  `RunnerSession` receive an owned-engine factory. Default engine construction, property decoding,
  and executable main methods belong to `tooling-launcher`; Gradle and IDE launch that assembly.

## Build conventions

- `build-logic/settings` (included as `build-logic-settings`, whose `check` the root `check` runs) owns the lean
  settings/composite convention classpath. Keep it independent of the producer and of project plugins such as
  architecture, publication, and the IntelliJ plugin. Its `build-libraries` plugin owns the library registry, the
  library repositories that `settings.gradle.kts` declares as data (`libraryRepositories { url(...) }`, searched in
  order by `pinLibraryReleases`), and checks that each library family root applies `module-library` and each
  folder holding segments applies `module-adapter`. Its `build-platforms` plugin checks that each project shipping
  platform agent data applies `module-platform-provider`.
- Segments check their linkage against the locked `[[release.artifact]]` closures that workers download, not the
  release module's dependency graph; worker tests run on the same closures through the `test-library-closures`
  convention. `compileForLegacyJava` compiles a source set for the legacy Java release and registers its
  `check<SourceSet>ClassRelease`; the `module-java-legacy` convention applies it to the main classes, and no other
  compilation gets the check. The platform provider convention checks the agent JAR against its version data.
- `settings.gradle.kts` declares the included builds and the standalone discovery exclusions; the root build
  wires their lifecycle (`check` runs the convention builds' checks, `build` builds the fixtures). Module build
  files must not repeat included-build task wiring.
- The root build's `prepareGradleFixtureRepository` publishes framework artifacts to the fixture repository.
  The `test-fixture-repository` project convention only connects a module's tests to that prepared repository.
- The standard Anvil plugin owns the executable runtime configuration and its lazy version-aligned
  dependencies. Keep source-set wiring, runtime dependencies, artifact mappings, and generated
  definition indexes internal to the build integration; expose only engine settings, EULA acceptance,
  and named artifacts through the normal `anvil` DSL.

- Tooling action/observation roots live under `tooling.extension.api.action` and `.observation`.
  Scoped scenario specializations live under `.action.scoped` and `.observation.scoped`; player and
  process variants, including capability-specific parents, live in their `player` and `process`
  subpackages. Registration accepts contribution objects through `action` and `observation` and
  rejects generic-base subclasses without a supported scope. Capability parents supply the current
  typed capability; keep identifier validation and registration lifetime enforcement in the runner,
  and do not restore target-specific registration overloads.
