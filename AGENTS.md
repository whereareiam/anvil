# AGENTS.md

## Purpose and scope

Anvil is a Java 21 testing framework for local Minecraft Java Edition and Bedrock Edition processes and
native-protocol players. Preserve reproducibility, protocol fidelity, complete cleanup, and useful failure
diagnostics. NeoForge is the supported mod loader and Lumi the supported Bedrock server. Kubernetes, SSH/hosted
orchestration, Fabric, Sponge, rendering, pathfinding, autonomous AI, and crafting automation are outside the
current project scope.

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
| `anvil-engine`                                                        | Global builder/registration, the process-property contract and host defaults for engine options, scenario structure validation, extensions, setup hooks, default ready contexts, active contexts, retention of process groups that outlive a scenario, and owned-resource cleanup; depends only on anvil-api |
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
| `anvil-launcher`                                                      | Public default builder, scoped-service adapters, default ScenarioFactory/per-run assembly, native worker bridge, and shaded packaging; wiring only, no rules of its own |
| `anvil-capability/capability-api`                                     | Owner-neutral composition, typed requests/handler registration, and neutral player identity/observations/dependency/lifetime contracts                                              |
| `anvil-capability/capability-protocol-api`                            | Protocol-backed player providers/contexts, channels/events, and native worker contracts                                                                                             |
| `anvil-capability/capability-agent-api`                               | Agent-backed process/player providers and scoped request-channel contexts; shared capability API only                                                                               |
| `anvil-capability`                                                    | Shared dependency validation/composition/cleanup for capability owners, discovery of installed agent-backed providers, per-library player runtimes, process/player facades, agent-provider adaptation, codecs, and worker bindings |
| `anvil-capability/capability-builtin/player/<feature>`                | Player capability family whose wiring-only root publishes the shaded `builtin-<feature>` bundle                                                                                     |
| `anvil-capability/capability-builtin/player/<feature>/<feature>-api`  | Public feature API (`builtin-<feature>-api`) and, for packet capabilities, the stateless release port in `.packet`                                                                  |
| `anvil-capability/capability-builtin/player/<feature>/<feature>-common` | Embedded, unpublished host provider, operation descriptors, and library-neutral binding in the feature root package                                                               |
| `anvil-capability/capability-builtin/player/<feature>/<feature>-<library>` | Segmented side folder holding the library's `WorkerExtension` (`builtin-<feature>-<library>`)                                                                                  |
| `anvil-capability/capability-builtin/player/<feature>/<feature>-<library>/V*` | Segments that implement only the feature's port for the library releases from their key on                                                                                 |
| `anvil-capability/capability-builtin/process/<feature>`               | Process capability family: `<feature>-api` and a root holding the agent-backed provider, independent of simulated players                                                           |
| `anvil-capability/capability-builtin/default`                         | Aggregate of every built-in player and process capability                                                                                                                           |
| `anvil-protocol/protocol-api`                                         | Protocol library providers, library hosts and releases, player creation/composition, protocol-owned channels/workers, pinned runtime resolution, the account workflow a library host offers, and under `.worker` the contracts of a library the native worker hosts (`WorkerLibraryProvider`, `WorkerLibrary`, `WorkerClient`, `WorkerAuthentication` with the Microsoft token it derives from, worker messages)                       |
| `anvil-protocol`                                                      | Player registration, version selection, authentication compatibility, composition, and observations                                                                                 |
| `anvil-protocol/protocol-worker`                                      | Wiring bundle publishing the native player worker once as the shaded `protocol-worker`: embeds `worker-common` and exports `protocol-api`; every library family depends on it |
| `anvil-protocol/protocol-worker/worker-common`                        | Embedded, unpublished worker host and shell: `WorkerLibraryHost` (the worker's one `ProtocolLibraryHost` registration), worker process, RPC client, class path, transport, `ProtocolWorker` entry point, capability registry, segment verification, session bindings, release reader/catalog, `ProtocolWorkerPool`, and the stored Microsoft accounts with their device-code sign-in (`HostedAccounts`) |
| `anvil-protocol/protocol-mcprotocol`                                  | Wiring bundle publishing the MCProtocolLib library: shaded `mcprotocol-common`, the client port, the client segments and a dependency on the `protocol-worker` bundle; owns `mcprotocol-releases.toml` and `pinLibraryReleases` |
| `anvil-protocol/protocol-mcprotocol/mcprotocol-api`                   | Release-neutral MCProtocolLib client port that client segments implement for the worker shell                                                                                       |
| `anvil-protocol/protocol-mcprotocol/mcprotocol-common`                | `McProtocolWorkerLibrary`, the library's `WorkerLibraryProvider`, the `WorkerClient` driving `McProtocolClient`, and `MicrosoftAuthentication`, which derives a stored account's Java Edition session; depends only on `protocol-api` |
| `anvil-protocol/protocol-mcprotocol/mcprotocol-client`                | Source-free side folder exporting one client segment per MCProtocolLib release whose client code differs                                                                            |
| `anvil-protocol/protocol-mcprotocol/mcprotocol-client/V*`             | Client segments `V1_18_2`, `V1_21_1` and `V1_21_11`, each implementing only `McProtocolClient` from its release key on; `V1_21_11` also serves `26.1.2`                             |
| `anvil-protocol/protocol-cloudburst`                                  | Wiring bundle publishing the CloudburstMC Protocol library for Bedrock players: shaded `cloudburst-common`, the client port, the client segment and a dependency on the `protocol-worker` bundle; owns `cloudburst-releases.toml` and `pinLibraryReleases` |
| `anvil-protocol/protocol-cloudburst/cloudburst-api`                   | Release-neutral CloudburstMC Protocol client port `CloudburstClient`, the native session `CloudburstSession<P, I, T>` that bindings receive, and the `ClientIdentity` a Bedrock login proves itself with                                           |
| `anvil-protocol/protocol-cloudburst/cloudburst-common`                | `CloudburstWorkerLibrary`, the `WorkerClient` driving `CloudburstClient`, offline and Xbox login identities, and `XboxAuthentication`, which derives a stored account's Bedrock Edition session; depends only on `protocol-api` |
| `anvil-protocol/protocol-cloudburst/cloudburst-client`                | Source-free side folder exporting the CloudburstMC Protocol client segment                                                                                                         |
| `anvil-protocol/protocol-cloudburst/cloudburst-client/V*`             | Client segment `V1_26_60`, implementing only `CloudburstClient` for the whole release and selecting each player's codec by its protocol number                                  |
| `anvil-platform/platform-api`                                         | Platform-provider/planning SPI, version data contract, artifact sources, distribution validation, and configuration contracts                                                      |
| `anvil-platform/platform-planning`                                    | Platform declaration validation, version data reading, effective Java/topology requirements, forwarding negotiation, the split of a plan by process lifetime, artifact planning, and provider preparation/configuration      |
| `anvil-platform/platform-*`                                           | Provider-specific distribution/configuration implementations and platform-agent assemblies                                                                                          |
| `anvil-agent/agent-api`                                               | Shared operation descriptors, payloads, identities, types, and exceptions; no client/server contracts or core dependency                                                            |
| `anvil-agent/agent-client/client-api`                                 | Host-side clients, directories, connections, and artifact lookup; exports shared agent contracts without core or capability APIs                                                    |
| `anvil-agent/agent-server/agent-server-api`                           | Embedded endpoint, native service, and operation provider contracts; exports only shared agent contracts                                                                            |
| `anvil-agent/agent-client`                                            | Host connections, stable process clients/directories, agent sessions, player observations, and artifact location                                                                    |
| `anvil-agent/agent-server`                                            | Embedded authenticated endpoint, native operation dispatch, extension loading, and the shaded agent artifact used by platform agents                                         |
| `anvil-integration/integration-junit`                                             | JUnit annotations, context injection, and lifecycle integration                                                                                                                     |
| `anvil-integration/integration-gradle/gradle-junit`                                      | Optional `AnvilTest` tasks (`anvilTest` and one per suite) and JUnit dependency wiring                                                                                             |
| `anvil-tooling/tooling-api`                                           | Editor-independent scenario, session, target, and log contracts                                                                                                                     |
| `anvil-integration/integration-gradle/gradle-tooling`                             | Gradle task discovery, definition indexing, source-set preparation, and tooling runtime                                                                                             |
| `anvil-tooling/tooling-runner`                                        | Reusable sessions, direct scenario-definition discovery, terminal commands, and structured protocol; supplied engine factory, no default launcher dependency                      |
| `anvil-integration/integration-intellij`                                                  | IntelliJ plugin assembly: `plugin.xml` including each module's descriptor fragment, the Settings → Tools → Anvil group, branding, and packaging; no code |
| `anvil-integration/integration-intellij/intellij-api`                                      | Contracts the IntelliJ features and build integrations share: `BuildIntegration`, `BuildIntegrations`, `ToolingCommands`, `AccountStaging`, `ScenarioSource` |
| `anvil-integration/integration-intellij/intellij-common`                                   | Standard implementations of the shared IntelliJ services and the IntelliJ test fixtures several modules share |
| `anvil-integration/integration-intellij/intellij-scenario`                                 | Scenario catalog, tooling launches, environments, consoles, actions, editor run icons, run configurations, tool window, and scenario settings |
| `anvil-integration/integration-intellij/intellij-account`                                  | Project and global accounts, pools, browser sign-in, account directories, and the accounts staged for a run |
| `anvil-integration/integration-intellij/intellij-gradle`                                   | Optional native Gradle project discovery, sync, and preparation adapter                                |
| `anvil-integration/integration-gradle/gradle-plugin`                                      | Standard scenario entry point assembling shared Gradle declarations, foreground tasks, and project discovery                                                                                         |
| `anvil-integration/integration-gradle/gradle-platforms`                                    | Gradle adapters for platform providers                                                                                                                     |
| `anvil-integration/integration-gradle/gradle-capabilities`                                 | Gradle adapters for capability providers                                                                                                                   |
| `anvil-testkit/tests`                                                 | Cross-module runtime and live assertions                                                                                                                                            |
| `anvil-testkit/fixtures`                                              | Independent consumer build for process, server-plugin, Lumi-plugin, and extension fixture JARs                                                                                                   |
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
  A family may consume another family's API only when declared through `architecture.dependsOnApis` on the
  family root, for two reasons: a protocol library's native-session API that its library-side segments
  implement a port against (the messages, movement, inventory and interaction families declare
  `cloudburst-api` for their cloudburst segments), and another capability's API that a capability uses
  when the player has it (the dialogs family declares `commands-api`).
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
- The native worker hosts libraries and names none. A library plugs in by implementing `protocol-api`'s
  `WorkerLibraryProvider` (package `protocol.api.worker`), registered through `ServiceLoader`: it describes itself as
  a `WorkerLibrary` (id, name, client port, release data, pin task, the offline UUID prefix, the packages its closures
  own, worker JVM arguments), builds the `WorkerClient` that drives its client port, and
  may supply a `WorkerAuthentication`. Its family shares `protocol-api` through `architecture.sharedApis`, and its
  wiring root depends on the `protocol-worker` bundle instead of embedding it. The worker's `WorkerLibraryHost` is
  the one `ProtocolLibraryHost` registration that installs every hosted library as a protocol library, and the
  worker JVM starts `ProtocolWorker`, which loads the library's provider by id. Never put a library ID, package ban
  or client type into `protocol-worker`.
- An Anvil account is one Microsoft account. `worker-common`'s `HostedAccounts` owns the one device-code sign-in
  (`MicrosoftSignIn`, the Minecraft Launcher's Xbox title `00000000402b5328` with scope
  `service::user.auth.xboxlive.com::MBI_SSL`, the application of the account files Anvil 1.0 stored) and the account
  files: one `<accountId>.json` (schema 3) holding the `MicrosoftToken`, per `MinecraftEdition` id the recorded
  `AccountAccess` (`AccountAvailability` with profile or reason) and per library ID its cached session. Availability
  belongs to the edition, not the library: a result counts for every installed library of the edition. Sign-in
  chooses no library and checks each edition that the hosted libraries' online releases speak once, with the first
  library of the edition and the newest version of its releases of that edition. A library's `WorkerAuthentication`
  only derives its session from the token (`derive(credentials, version)`), returns the token as it left it, and
  refuses an account its edition's services refuse with a reason it words itself, naming the account; neutral code
  names no edition. Every derivation, at sign-in, for each player (under the edition of the player's release) and
  for `ProtocolAuthentication.check(accountId, edition)`, records the result for its edition. Schema 1 files (one
  library's serialized MinecraftAuth session under `credentials`, named by `provider`) and schema 2 files (results
  per library ID) are read into that shape without a new sign-in: a library's record becomes the record of the one
  edition its online releases speak, an available record winning, and a library that is not installed or speaks
  several editions leaves its edition unknown. The IDE, which knows no library, shows those files as not checked. `ProtocolLibraryHost.authentication(...)` offers the workflow
  and `ProtocolLibraryRegistry.authentication(...)` returns it; there is no per-library authentication.
- A CloudburstMC Protocol client segment hands bindings a `CloudburstSession<P, I, T>` as the native session: `send`,
  typed `subscribe` dispatched from the session's one packet handler, the `GameStart`, the item definitions and the
  client tick. Each session is one connection generation, and closing its connection ends its subscriptions, so a
  reconnect never reaches them. The session also names the profile its login claimed and whether the server is
  authoritative over movement: always from protocol 818 on, before that as the game start or a later movement
  authority packet says. CloudburstMC types reach feature segments only through the type parameters; host
  code and `cloudburst-common` never see them. The session, messages, movement, inventory and interaction
  capabilities serve `mcprotocol` and `cloudburst`; their `<feature>-cloudburst/V1_26_60` segment serves every codec
  of the release. Commands and dialogs are Java Edition behaviour and serve only `mcprotocol`; a Bedrock player
  asking for them is told the libraries that provide them.
- Like a real client, a Bedrock session tracks its own player and world per connection generation: item stacks of
  the player's containers (inventory, off hand, armor, user interface with the cursor) and of opened containers, the
  selected hotbar slot, the position and rotation at the eyes, and the blocks of the player's dimension. Under server
  movement authority it also sends a player authoritative input on every tick from its spawn on, repeating the tracked
  position, so one move stays in effect after a server ignored it while completing a teleport. Trackers and that
  `InputTicker` live in their own classes in the client segment (`InventoryTracker`, `PositionTracker`,
  `InputTicker`, and `WorldTracker` in package `client.world.v1_26_60`); `ConnectionSession` only wires them. They see each received packet before subscriptions
  and the player's trackers see each sent packet before it leaves, so a feature's own send is the client's
  prediction, settled by item stack responses or the server's corrections. `blockAt(x, y, z)` returns the
  first-layer runtime id the server sent for a block, as `WorldTracker` keeps the chunks like a client: full level
  chunks, sub-chunks it requests in request mode, block and sub-chunk updates, dropped on a dimension change or
  outside the chunk publisher; air-only and never-received blocks are unknown. Inventory clicks follow the
  game start: item stack requests under server inventory authority, otherwise normal inventory transactions, which
  Lumi expects. Feature segments refuse what the session cannot send faithfully (shift clicks, stacking onto the
  same item, opened containers under server authority, the off hand where the codec has no hand, a block use
  whose block the session has not received) and ignore Java-only port values such as state IDs and sequences.
- Use `ServiceLoader` for the existing provider SPIs. Preserve service descriptors and merge them
  when shading. Do not add broad reflection scanning as an alternative discovery path.
- Each player selects its protocol library: its own `protocolLibrary`, else the scenario's, else the engine's,
  else the one installed library whose release supports its Minecraft version most strongly; a tie requires an
  explicit choice. A stored or leased account never changes the library. Launcher composition
  creates one capability runtime per selected library.
- Editions are platform-agnostic: `api.model.MinecraftEdition` is an opaque identifier compared only for equality,
  with no constants or default, and Java code never names an edition; the identifiers appear only in each family's
  data files. A platform declares `edition` in its version data; planning records it in `ProcessPlan`, refuses a
  proxy whose backend servers speak different editions (`BackendEditionValidator`), and the
  launcher passes `PlatformPlan.editions()` to the player service. Each protocol release row declares its own
  `edition` (a library is not single-edition) and maps its versions to protocol numbers, `protocol = N` or a
  `[release.protocol]` table, read by `ProtocolRelease.protocolNumber(MinecraftVersion)`. Selection compares
  (edition, version): only releases of the reachable servers' edition match, across all installed libraries;
  release keys stay unique within a library because segments name them. A protocol-backed capability whose
  providers all exclude the player's library reports the libraries that provide it.
- Public capabilities extend `PlayerCapability` or `ProcessCapability`, both rooted in the neutral
  `Capability` contract. Dependencies remain within the same owner. Neutral player providers use
  shared observations and dependencies. Protocol-backed providers declare library support through
  `supportedLibraries()`; agent process providers select supported platform IDs.
- Retrieve only declared capability dependencies: `requiredCapabilities`, without which a provider is not
  created, and `optionalCapabilities`, which are created first when the owner has them and are absent from
  the context otherwise; only a missing required capability makes a provider unavailable. The agent-backed
  `Server` capability must remain independent of `Session`.
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
- Tooling signs in through `ProtocolLibraryRegistry.authentication(...)`, not the worker's account files, and never
  chooses a library. Account files are managed outside project configuration. The IntelliJ account manager and
  Gradle `anvilLogin`/`anvilLogout` share the `AnvilAuthentication` entry point; the IDE reads account files
  without any library. An online player whose account is recorded as unavailable for the edition of the servers it
  reaches is refused before it connects, naming the account and giving the recorded reason. Named pools live in the account directory's
  versioned `pools.properties`, documented on `AccountManager.pool(String)`; the runtime reads it and the IDE writes
  it, with the same rules. A pool lends a player only an account available for the edition of the servers it reaches
  (`AccountPool.lease(MinecraftEdition)`, `PlayerManager.create(options, pool)`); when it has none, it checks its
  free accounts of unknown availability in order through `ProtocolAuthentication.check`, one network derivation
  each while the account is reserved, records each result and lends the first that becomes available, and otherwise
  fails listing each account and why. JUnit's `@AnvilAccounts(edition = "...")` counts, and pools, only accounts
  available for that edition; without it, every stored account counts.
- External embedded-agent handlers implement `agent.server.api.operation.AgentOperationProvider`
  and own their operation namespace.
  Install their JARs under `plugins/anvil-agent-extensions`; do not bundle Anvil agent APIs or
  platform SDKs into those JARs. Host capabilities receive scoped request channels; launcher adapters
  bind them to connections obtained through `AgentDirectory`.
- Platform agents expose native services and their scheduling rules. The endpoint owns its extension
  class loader; the scenario owns host connections.
- `executionProviderId` selects providers in declarations and execution plans; `RunningProcess.executionId()`
  identifies one execution attempt. Engine `ProcessTimeouts` defaults are overridden independently by scenario
  startup/shutdown values. Engine `ProcessScheduling` parallelism and startup memory govern each scenario operation,
  not aggregate engine usage; its `processors` becomes each process's `-XX:ActiveProcessorCount` and its
  `priority` reaches the execution provider, which applies it or refuses the environment.
  Keep artifact download concurrency separate.
- `JavaSelection` groups Java requirement and source in engine, scenario, and process declarations.
  Omitted members inherit independently; an explicit empty requirement overrides an inherited version
  and selects the platform's preferred LTS. `JavaRequirementPlanner` supplies execution a selection with
  an exact feature version. Scenario listener binding and LAN permission belong to `NetworkPolicy`,
  alongside exposure and access policy.
- Processes run on LTS releases only (11, 17, 21, 25, then every fourth); Java 8 is not one of them.
  Each provider owns `<platform>-versions.toml` (`edition`, Java rows, `[agent] minimumJava`, verified and known
  versions) and exposes it through `PlatformProvider.versionData()`; platform-planning reads it, so
  platform-api carries no parser. A table named after the platform ID holds data only the provider reads;
  planning skips it and refuses every other unknown key. The agent minimum raises the effective minimum and the default LTS;
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
  planner's defaults, spelling each version as that data writes it (`1.20.0` stays `1.20.0`).
- Java requirements are execution-agnostic. Local homes and verified archives belong to Java provisioning;
  Docker image mappings belong to the Docker execution provider.
- Execution providers own network topology and endpoint translation. A local loopback bind is host exposure
  control, not process isolation; strict proxy-only isolation is rejected unless the provider can enforce it.
- Execution sessions reserve host listener ports through the engine's `PortReservations` and release them when
  they close. Managed execution's `PortSelection` picks them below the ephemeral ranges, because a process
  binds its port seconds after preparation selected it; do not probe port 0 in a provider.
- A platform's `PlatformProvider.gameTransport()` (`api.type.network.ListenerTransport`, TCP by default) travels
  through `ProcessPlan`, `ProcessRequest` and `ProcessTarget` to `RunningProcess.transport()`. Ports are reserved,
  probed and published with that transport while agent ports stay TCP; reserved numbers stay distinct across
  transports. Planning refuses a proxy whose backends use another transport, and a player's source-address probe
  binds with the transport of its target.
- A simulated player's name is its unique identity within the scenario; its Minecraft username defaults to
  the name and may be shared by several players. The client records the profile its login success granted,
  which a proxy or login plugin may have renamed. Player observations follow the player's own connection from
  the process it connects to, ask each agent for that granted UUID, and never look a player up by username,
  which platforms match without regard to letter case. Routes name scenario processes. A client reports as granted
  the UUID the server shows it; an agent whose platform keeps another UUID for the player matches the shown one and
  reports the platform's own in `AgentIdentity`, which becomes the observed profile (the Lumi agent matches
  `getLoginUuid()`). An offline player's requested UUID is the name-based UUID of its library's
  `offlineUniqueIdPrefix` and username: `OfflinePlayer:` for MCProtocolLib, `BedrockPlayer:` for CloudburstMC Protocol.
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
- Every public consumer task is in the `anvil` group: `anvil` (all live tests), `anvilTest`, `anvil<Name>Test`
  per suite, `anvilScenarios`, `anvilRun`, `anvilLogin` and `anvilLogout`. `anvilTooling` has no group because
  editors run it. Ordinary `test` and `check` never depend on a live-test task.
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
- Lumi, a Bedrock Edition server, selects `Distribution.remote(bedrockVersion, lumiRelease)`, with versions named
  as Lumi names them (`1.26.50`). `lumi-versions.toml` declares the `bedrock` edition and, in its provider-owned
  `[lumi]` table, the protocol of each version and the versions each release accepts; the provider refuses an
  unlisted release, a release that does not accept the version, and `latest`. The JAR `com.koshakmine:Lumi` comes
  from `https://repo.lumi.su/releases`, verified against the repository's SHA-256 or an explicit pin. The game
  listener is UDP. `settings.yml` is written structurally before each start with `min-protocol` = `max-protocol` =
  the version's protocol and `xbox-auth` = online mode; Lumi has no identity forwarding and no Yggdrasil, so
  its only forwarding mode is `NONE` and `sessionServer` is refused. The Lumi agent is a Lumi plugin compiled
  against the release in the catalog. Lumi ships an older Jackson and loads plugin classes parent first, so the
  plugin is only a bootstrap: the agent (its `agent` source set) and the agent server with its own Jackson form a
  nested JAR that the plugin loads in a child-first `AgentClassLoader` sharing only the JDK and `cn.nukkit`, and
  closes on disable; extensions load beneath it and see the agent's Jackson, as on Paper. `checkAgentJava` checks
  nested JARs too. Lumi servers load no Bukkit plugin, so their live scenarios carry the Lumi
  fixture plugin `test-lumi-plugin` instead of `test-server-plugin`: `LumiServerSystemTest` drives them through
  the console and the agent, and `BedrockPlayerCapabilitiesSystemTest` and `BedrockPlayerSessionSystemTest` check
  each Bedrock player action with vanilla commands (`testfor`, `testforblock`, `clear`) through the agent's console
  execution, except what no vanilla command shows: the fixture spawns an entity, reports its runtime ID and reports
  the player's interaction with and attack on it. The matrix runs
  Lumi's oldest and newest version and both sides of the 1.21.90 movement-authority boundary (`1.21.80` with
  `client-auth`). `known` lists exactly the versions whose protocol Lumi and the cloudburst row agree on,
  spelled the same on both sides; `1.26.45` is left out (Lumi 2170, CloudburstMC Protocol 2169).
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
- A process declared with `ProcessLifetime.ENGINE` is not part of its scenario's process group. Planning's
  `LifetimeSplit` says which processes outlive a scenario, plans them as a scenario of their own, identifies
  equal declarations, and gives the scenario's own processes the forwarding settings the kept ones run with.
  The engine's `RetainedProcesses` lends one running group per identity to one scenario at a time and
  `JoinedProcessGroup` presents it with the scenario's own. The launcher only assembles: `RetainedProcessSet`
  starts a group with its agents and capabilities and `ProcessLease` binds a scenario to the one it is lent.
  `ScenarioFactory.create` may start that set, because the scenario's own processes are prepared against it.
  A successful scenario returns the set; a failed one stops it and keeps its workspaces. Do not add scenario
  state to a retained set, and do not share one between scenarios that run at once.
- The launcher holds no rules. Its `assembly` package constructs services and hands them to each other, `adapter`
  implements one family's contract on another family's service without deciding anything, grouped by `execution`,
  `player` and `provisioning`, and `worker` is the same kind of binding for the native player worker JVM.
  Behaviour that needs only `anvil-api` belongs in the engine and behaviour over one family's types in that family:
  capability discovery in `anvil-capability` (`ProcessCapabilityRuntime.discover`, `LibraryCapabilityRuntimes`), the
  order of a process's JVM arguments and the lifetime split in platform planning. `ExecutionPlanMapper` is the one
  translation between two families' plans and stays an adapter. In-repo consumers compile against the launcher's
  shaded JAR, which carries the embedded modules' public types such as `engine.config`.
- The engine prepares and starts scenarios concurrently; shared services reached from `ScenarioFactory.create`
  or a start must be safe for that. JUnit uses one engine for the whole test run.
- Start servers before proxies; stop processes in reverse dependency order, those of one startup layer together. Cleanup attempts every resource,
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
  for a new protocol (version, edition, module, protocol, Minecraft versions, verified versions, Java, features), and
  pin its closure with `pinLibraryReleases`. Add a segment only to a side whose newest segment fails
  `checkSegmentLinkage` against the new closure, named after the new release key; a segment folder must name
  a release key, so raising a key renames that segment. Add the release to `McProtocolWorkerContractTest`,
  platform `known` versions and `[[java]]` rows only where Java changes, and mark as verified exactly what
  `CompatibilityScenarioFactory` runs; every other listed version is COMPATIBLE, and user-supplied release
  data is UNTESTED. Update the versions page, the Java table, the README, and the FAQ. Neither `protocol-worker` nor
  `McProtocolWorkerLibrary` imports MCProtocolLib; release-specific client code lives only in `mcprotocol-client` segments, whose
  `linkage.txt` requirement keywords the worker's self-check enforces. A Bedrock version extends CloudburstMC
  Protocol's one `cloudburst-releases.toml` row: the newer snapshot build, the version in `minecraft` and its number
  in `[release.protocol]`, written `1.26.x` for Bedrock 26.x as the library and Bedrock servers write it, its codec
  in the client segment, and the segment renamed to the new key. A release with an unpinned closure is
  listed but cannot be launched: preparing a scenario whose servers would use it by default prints a warning,
  and creating a player that selects it is refused.

## Tests and verification

- Unit and focused integration tests stay in their owning module's `src/test`, normally mirroring
  the production package. Test observable contracts rather than duplicating implementation details.
- IntelliJ tests belong to the module that owns the behaviour; `intellij-api` has no test sources. Each
  module's tests load its real descriptor fragment through a test-only `META-INF/plugin.xml` that adds
  the shared fragments and stand-ins for contracts another module implements. Only descriptor
  composition tests belong to the `integration-intellij` root. Share fixtures that several modules use,
  such as `SwingTestSupport` and `ControlledToolingProcess`, through `intellij-common/src/testFixtures`,
  never through production artifacts or another module's whole test tree.
- `anvil-testkit/tests/runtime/src/test`: cross-module discovery/composition without live Minecraft.
- `anvil-testkit/tests/server/src/test`: real sessions, capabilities, routes, and external agents,
  grouped by behavior. The whole task requires `-Panvil.testMode=full`; only its `matrixTest` task, which
  checks `CompatibilityScenarioFactoryTest` without starting a server, runs in every build.
- The independent `anvil-test-fixtures` consumer build under `anvil-testkit/fixtures` contains
  `test-process`, `test-server-plugin`, `test-lumi-plugin`, and `test-extension`. They use `src/main`; dependencies on Anvil
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
./gradlew :anvil-protocol:protocol-worker:worker-common:test
./gradlew :anvil-protocol:protocol-mcprotocol:mcprotocol-common:test
./gradlew :anvil-protocol:protocol-cloudburst:cloudburst-common:test
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
  and call `reusable-verify.yml`, `reusable-intellij.yml`, `reusable-live.yml` and `reusable-publish.yml`; shared steps come from `whereareiam/devops`, and
  only Anvil-specific ones live in `.github/actions`. Pull requests automatically get the metadata check and
  the quick `build` checks, which start no server. Full verification with the IntelliJ plugin, standalone
  consumers and real platforms is maintainer-requested through `workflow_dispatch` and reports a
  `Full verification` status on the pull request's head commit. Development builds are manual-only and
  publish a branch-qualified version. Release Drafter updates on `dev` pushes or manual dispatch,
  using `feature`, `change`, `bug`, `dependencies`, `major`, and `skip-changelog` labels. Published
  releases trigger full verification, Maven publication and IntelliJ Marketplace publication. No scheduled
  nightly workflow is required. A clean machine runs `publishToMavenLocal -Panvil.bootstrap` before any other
  task: the bootstrap build leaves out the server suite, which applies the plugin at the build's own version.
  Only `reusable-intellij.yml` and the Marketplace publication resolve the IntelliJ modules; every other CI
  build passes `-Panvil.intellij=false`, because the IntelliJ Platform Gradle Plugin's IDE indexing fails now and
  then on a fresh machine. A development build does not wait for the IntelliJ run; a release does.

## Pull requests and releases

Build each change in its own `feature/...` branch and merge it into `dev` through a pull request.
Publishing a release draft tags `dev`, verifies it with the live suites, publishes the Maven artifacts and
Gradle plugins, and then the IntelliJ plugin. Development builds keep their `<branch>-<short commit>`
version, such as `dev-a123bcd`, because consumers resolve them by that version.

### Release notes

A merged pull request lands in the release draft as `* <title> ([#<number>](<url>))`, under the
category of its label. Readers are developers who test with Anvil or extend it.

- Titles use `Area: Description`, with a past-tense description of what changed for them:
  `Introduced`, `Changed`, `Fixed`, `Removed`. Describe the effect, not the implementation.
- The draft sorts by the exact area, so spell an area exactly as listed: `Engine`, `Environments`,
  `Workspaces`, `Execution`, `Processes`, `Platforms`, `Players`, `Capabilities`, `Protocol`, `Yggdrasil`,
  `JUnit`, `Gradle`, `IntelliJ`, `Launcher`. A change to one platform only may use its name: `Paper`,
  `Spigot`, `NeoForge`, `Lumi`, `Velocity`, `BungeeCord`. A change to one protocol library, MCProtocolLib or
  CloudburstMC Protocol, uses `Protocol`, which readers know both libraries by. Add an area only for something readers recognise by that
  name, such as a new platform, protocol library or integration, and add it to this list in the same
  pull request. Never use module or package names such as `Architecture` or `Integration`.
- A `skip-changelog` pull request still needs a title in that format; `CI`, `Build`, `Docs` and `Tests`
  are fine there.

| Label | Use for |
| --- | --- |
| `feature` | something new for tests, build scripts or extensions |
| `change` | changed behavior, DSL, tasks or API |
| `bug` | a fix for a bug that shipped in a release |
| `dependencies` | a library update |
| `skip-changelog` | refactors, build, CI, tests and documentation, and fixes of bugs that never shipped |

A fix for a bug introduced after the last release belongs to the work that introduced it, so it is
`skip-changelog`; compare against the last release tag (`git log <tag>..HEAD -- <path>`) to decide. Add
`major` next to the category when projects must change their tests, build scripts or extensions on upgrade.

Text written by hand, the subtitle, the summary, highlights and upgrade notes, lives in
`.github/release-notes.md`; it replaces the draft's placeholders while its `version` matches the drafted
version, so notes of a published release never reach the next draft. When the file names an already
published version, start the next release's notes by replacing the whole file. The release workflow stops
while a placeholder is left. Fix the changelog part by editing titles and labels of merged pull requests,
then rerun the `Release drafter` workflow; text typed into the draft is lost on the next push to `dev`.

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
- The IntelliJ plugin is split by feature. The `integration-intellij` root is the plugin and holds no
  code: its `plugin.xml` includes `anvil-common.xml`, `anvil-scenario.xml`, and `anvil-account.xml` in that
  order, loads `anvil-gradle.xml` and `anvil-scenario-kotlin.xml` with their optional plugins, and declares
  the Settings → Tools → Anvil `groupConfigurable`. Each module registers its own services, actions,
  settings pages, and extensions in its fragment. Feature modules are implementations and depend only on
  `intellij-api`, `anvil-api`, and `tooling-api`; a feature reaches shared behaviour through a service
  interface in `intellij-api` implemented by another module (`BuildIntegrations` and `ToolingCommands` by
  `intellij-common`, `AccountStaging` by `intellij-account`). Declare extension points with their
  `qualifiedName`, so a fragment means the same in the plugin and in a module's test descriptor.
- Features meet in the UI through the IDE's action system, never through each other's classes. Scenario
  views offer their selection through `UiDataProvider` with `ScenarioSource.DATA_KEY` and
  `ScenarioDataKeys`, and show the registered groups `Anvil.Catalog.Toolbar`, `Anvil.Catalog.Popup`,
  `Anvil.Environment.Toolbar`, and `Anvil.Environment.Popup`; another module adds its actions to those
  groups from its own fragment, as the account module adds **Anvil Accounts…**. Views never decide what a
  command does: actions in `scenario.action` read the data context and describe why they are unavailable.
- Every entry point that runs a scenario goes through `EnvironmentLifecycle`: the catalog, the editor
  gutter (`ScenarioLineMarker` with `ScenarioConfigurationProducer`), run configurations, and environment
  tabs, which act on their own `EnvironmentSession` when it already runs the scenario. `availability`
  returns an `EnvironmentSession.Availability` whose reason the action shows; `run` applies the same rule.
  A failed request or a failed environment out of view becomes a notification in the `Anvil` group, never a
  replacement of the catalog.
- `intellij-scenario` groups packages by what they own, with a `view` subpackage for Swing screens:
  `tooling` (`ToolingHost`, `ToolingLaunch`, `ToolingConnection`, and `tooling.protocol`), `catalog`
  (`ScenarioCatalog`, `catalog.source.SourceDiscovery` with `ImportRefreshPolicy`, `catalog.view` with the
  tree), `environment` (`EnvironmentLifecycle`, `EnvironmentSession`, `EnvironmentExecution`, and the tab
  pages in `environment.view`), `action`, `editor`, `runconfiguration`, `log`, `console`, `navigation`,
  `settings`, and `window` (tool-window factory, `ToolWindowController`, `ScenarioPresentation`, shared
  components). Concrete project and application services are light services (`@Service`); only shared
  contracts are interfaces. Types another package family needs are nested in their owner:
  `ScenarioCatalog.Snapshot`/`State`, `SourceDiscovery.Snapshot`/`State`, `EnvironmentSession.Status`,
  `CommandHistory.Scope`, `ScenarioSettings.Values`/`Section`/`History`.
- `ToolingHost` arbitrates reuse, reservation, and replacement of the project's one tooling launch, and
  `ToolingLaunch` owns preparation, the runner, the staged accounts, and cleanup. Its private
  `LaunchResources` owns partially acquired files and accounts; try-with-resources releases both even when
  another cleanup step fails. `ToolingConnection` drains diagnostic delivery before file cleanup.
  `ToolingLaunch` publishes one typed `ToolingLaunch.Outcome` after cleanup and requests scenario
  discovery only when asked; listener failures cannot skip resource release or pending-future completion.
  A replacement waits for the previous launch's cleanup. An `EnvironmentSession` stays bound to its
  execution; commands never look up a replacement through a project service. `SourceDiscovery`, once
  initialized, observes the project independently of open views, and reloads wait for environment cleanup.
- `ToolingClient` owns handshake, ordered request submission, response correlation, and pending-result
  completion on closure or failure. `ToolingMessageCodec` owns typed JSON binding and version checks;
  transport frames stay internal. Portable `tooling-api` payloads use Jackson-compatible Lombok builders
  and are reused by both peers; do not recreate their fields in a manual JSON model reader.
  `ScenarioOperations`, `EnvironmentOperations`, and `ProcessOperations` declare predefined
  `ToolingOperation<Q, R>` schemas at the `tooling-api` root. `ToolingOperationRegistry` binds typed runner
  handlers and cancellation policy; `ToolingRequestReader` decodes registered payload types generically.
  Do not add per-operation codec factories or duplicate operation enums. `ToolingProtocolWriter` emits typed
  envelopes. Protocol 7 uses nested action targets. Session controls are explicit methods, never string
  protocol operation names. Response IDs correlate requests and must not be overloaded with operation names.
- A run configuration stores a source, a definition class, and an optional process; one definition class
  declares exactly one scenario, so the class is the scenario's identity throughout the plugin.
  `RunConfigurationService` saves, validates, and launches configurations; `AnvilRunConfiguration` suppresses
  the debug executor and gives the Run tab its own console presentation. Running from the catalog does not
  create a configuration.
- `intellij-account` keeps `AccountLibrary` (the project and global directories it reads), `storage`
  (account and pool files, `StagedAccountDirectory`, `ProjectAccountStaging`), `AccountSignIn` (one browser
  sign-in with its processes and manifest), `settings` (`AccountSettings` and the Accounts settings page,
  which owns both directories and global participation), and `view` (the account window). Account metadata
  holds no protocol library: the lists show each account's profile names with an `EditionChip` per edition
  it is available for, and files of schemas 1 and 2 show the profiles they recorded per library
  (`AvailableAccount.libraryProfiles`) until the runtime rewrites them. Sign-in never chooses a library.
- Each feature owns its settings: `ScenarioSettings` with the Scenarios page and `AccountSettings` with the
  Accounts page, both under the `me.whereareiam.anvil` group. Name persistent components and storage files
  after their feature (`AnvilScenarios`, `anvil-scenarios.xml`) and keep them stable when renaming classes.
- Define all panels and dialogs in Kotlin under the module's `src/main/kotlin`, using Kotlin UI DSL for forms
  and Swing composition for tool-window layouts, beside Java controllers, services, and platform adapters in
  the matching `src/main/java` packages. Forms own layout, editable values, presentation validation, and
  local control interactions; Java owns service calls, persistence, subscriptions, history policy, and
  operation lifetimes. Use ordinary methods and Java callback types at that boundary. The `module-intellij`
  convention sets the Kotlin API to the baseline IDE's bundled runtime for every module applying the Kotlin
  plugin; the root project declares the Kotlin plugins with `apply false` so its children share one class
  loader. Feature modules apply Kotlin's Lombok plugin so views can use members Lombok generates.
  Native IntelliJ project and disposal types are allowed in IntelliJ contracts; Gradle SDK types stay in
  the Gradle adapter. Reserve the repository's `/run/` directory for generated runtime files.
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
