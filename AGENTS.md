# AGENTS.md

## Purpose and scope

Anvil is a Java 21 testing framework for local Minecraft Java Edition processes and native-protocol
players. Preserve reproducibility, protocol fidelity, complete cleanup, and useful failure diagnostics.
NeoForge is the supported mod loader. A scenario runs on a workbench: the local workbench in the engine's own JVM,
or a paired workbench on another machine. Kubernetes, Fabric, Sponge, Bedrock, rendering,
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
  `JavaMovementExtension`, `SpigotDistributionResolver`, and `WorkspacePlanValidator`.

## Module ownership

| Area                                                                  | Responsibility                                                                                                                                                                      |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `anvil-api`                                                           | Global engine registration, scenario definitions/lifecycles, public process/player handles, and capability owner identities; no scoped services or project dependencies             |
| `anvil-engine`                                                        | Global builder/registration, the process-property contract and host defaults for engine options, scenario structure validation, extensions, setup hooks, default ready contexts, active contexts, retention of process groups that outlive a scenario, and owned-resource cleanup; depends only on anvil-api |
| `anvil-environment`                                                   | Organizational grouping for independent cache, provisioning, and execution families: the building blocks a workbench is assembled from, which a runner may add to or replace                                                                                                 |
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
| `anvil-workbench/workbench-api`                                       | The workbench contract: its bound execution providers, provisioning, and `WorkbenchFacts`; carries the environment APIs it hands out |
| `anvil-workbench/workbench-common`                                    | `RequirementCheck`, which refuses a scenario whose declared and implied requirements a workbench's facts cannot meet, and the machine's capacity                          |
| `anvil-workbench`                                                     | The local workbench: the filesystem cache, artifact, Java and workspace provisioning, ports, image locks and execution providers assembled in the engine's JVM; assembly only |
| `anvil-workbench/workbench-protocol`                                  | The protocol between an engine and a workbench on another machine: typed requests, answers and connection headers as JSON lines over TLS, the mapper for Anvil's models, `WireFailure` error translation and certificate pinning; an API that carries behaviour by design, like the worker kits |
| `anvil-workbench/workbench-remote`                                    | `RemoteWorkbench`, the engine's side of a workbench on another machine: proxies for execution, provisioning and files, port mirroring, uploads, pairings and `WorkbenchChoice` |
| `anvil-workbench/workbench-server`                                    | The `anvil-workbench` JAR and image: serves engines over TLS from a data directory under its operator's `workbench.toml`, and on an engine's machine runs the pairing commands; assembly only |
| `anvil-launcher`                                                      | Public default builder, scoped-service adapters, default ScenarioFactory/per-run assembly, and shaded packaging; wiring only, no rules of its own |
| `anvil-capability/capability-api`                                     | Owner-neutral composition, typed process requests, and neutral player identity/dependency/lifetime contracts                                              |
| `anvil-capability/capability-common`                                  | Shared dependency validation/composition/cleanup for capability owners, agent extensions of installed process providers, per-library player runtimes and the player composer, and process/player facades |
| `anvil-capability/capability-player/capability-player-api`            | Protocol-backed player providers and their contexts over the player's `ProtocolChannel` (artifact `capability-protocol-api`), and in its test fixtures the `TestProtocolPlayerCapabilityContext` provider tests create capabilities in |
| `anvil-capability/capability-player/capability-player-builtin/builtin-<feature>` | Player capability family whose wiring-only root publishes the shaded `builtin-<feature>` bundle |
| `anvil-capability/capability-player/capability-player-builtin/builtin-<feature>/<feature>-api` | Public feature API (`builtin-<feature>-api`) and, in `.channel`, the `<Feature>Channel` contract between host and worker: capability ID, operation and event descriptors |
| `anvil-capability/capability-player/capability-player-builtin/builtin-<feature>/<feature>-common` | Embedded, unpublished host provider and host capability in the feature root package |
| `anvil-capability/capability-player/capability-player-builtin/builtin-<feature>/<feature>-java` | The feature's worker extension over the Java Edition protocol (`builtin-<feature>-java`), free of any release |
| `anvil-capability/capability-process/capability-process-api`          | Agent-backed process providers and scoped request-channel contexts; shared capability API only (artifact `capability-agent-api`) |
| `anvil-capability/capability-process/capability-process-builtin/builtin-<feature>` | Process capability family: `<feature>-api` and `<feature>-common` holding the agent-backed provider, independent of simulated players |
| `anvil-capability/capability-process/capability-process-builtin/builtin-players` | Players family: what one server or proxy knows about the players on it, from its own agent's events, with `players-api` and `players-common` |
| `anvil-capability/capability-process/capability-process-builtin/builtin-<feature>/<feature>-agent` | A process family's agent channel (`builtin-<feature>-agent`): the agent operations and payloads its host provider and platform sides share, and the platform-neutral handlers, compiled for the legacy Java release |
| `anvil-capability/capability-process/capability-process-builtin/builtin-<feature>/<feature>-<platform>` | A process family's agent extension for one agent platform (`builtin-<feature>-<platform>`), such as `-bukkit` for Paper and Spigot, over that platform's own API |
| `anvil-capability/capability-process/capability-process-builtin/builtin-worlds` | Worlds family: a server's worlds, where its players are, moving them and the world changes it reports, with `worlds-bukkit` and `worlds-neoforge`, whose `release-<minecraft>` parts each serve the NeoForge releases from one change of Minecraft's names on |
| `anvil-capability/capability-default`                                 | Aggregate of every built-in player and process capability (artifact `default`) |
| `anvil-player/player-api`                                         | Protocol library providers and releases, player creation/composition, the typed player channel and worker extension contracts, the styled text a player sees (`model.text`), pinned runtime resolution, and optional authentication                       |
| `anvil-player/player-common`                                         | Player registration, version selection, authentication compatibility, composition, observations, and resolving a remote's address as the vanilla client does |
| `anvil-player/player-worker`                                         | Worker runtime kit every library embeds to run worker extensions: `WorkerExtensions`, `NativeSessionBindings`, `WorkerProtocols` and `WorkerOperation`, and in its test fixtures the `TestWorkerPlayer` and `TestOperations` that extension tests bind to |
| `anvil-player/player-protocol/protocol-java/protocol-java-api` | Java Edition protocol of simulated players: `JavaProtocol` and the packets, models and types it carries, as release-neutral values, the `ClientboundFrame` every received packet also arrives as, the `ClientboundPackets` catalog naming each packet value's kind, the client's `JavaWorld` that its packets keep and the client's own `LocalPlayer` |
| `anvil-player/player-protocol/protocol-java/protocol-java-geometry` | Vanilla's math, bit for bit as the client of each version computes it: `Vector`, `Box`, `Axis`, `Coordinates`, `VectorLength` and the table sine of each version (`Trigonometry`), which the worker kit's teleports and the physics kit share; no Anvil dependency |
| `anvil-player/player-protocol/protocol-java/protocol-java-worker` | Java Edition worker kit: `JavaPacketDispatcher`, the player's `JavaProtocol` over the `JavaConnection` a library implements, with the store of the player's `JavaWorld`, the `wants` a library asks before it decodes a costly packet and the `wantsFrames` it asks before it hands a packet over as its frame, which the dispatcher names from `protocol-java-packet`; in `position` the client's own player every Java player has (`ClientPosition`, the `LocalPlayer` the dispatcher hands out): the client's 20 Hz loop (`ClientLoop`), the position, view, velocity and ground state it knows, the teleports it resolves (`Teleport`) and accepts, the wait for its level (`LevelLoading`) and the version differences of all that (`LocalPlayerBehaviour`); and in its test fixtures the `TestJavaPlayer` that speaks it and whose client a test ticks itself |
| `anvil-player/player-protocol/protocol-java/protocol-java-block` | Block data of every supported protocol by global block state ID (`JavaBlockData`, `BlockRegistry`), keyed by protocol number because every Minecraft version of a protocol shares one registry; read from each release's own vanilla client by the physics harness's `generateVanillaData` and committed as `vanilla/<protocol>.blocks`: the blocks and states in global ID order with their properties, each state's collision shape for a standing player (`CollisionContext.of(player)`) with the shape of every other player case that differs (`CollisionCase`) and the unmoved shape of an offset block with its `BlockOffset`, friction, speed, jump and bounce, stuck speeds, fluid state, fluid flow and sturdy faces, the kind, collision kind and behaviours vanilla's movement code asks by class, identity or tag, and the movement tags it reads; nothing is maintained by hand. The `module-block-data` convention's Java `generator` source set (`BlockDataGenerator` and `VanillaBlockReader`, run by `generateBlockData`) reads the files strictly and writes the data through the package-private `BlockDataCodec`, which `JavaBlockData` reads it with, so the enums and the format exist once; `checkBlockDataReleases` requires a file for the protocol of every protocol library release and none for another |
| `anvil-player/player-protocol/protocol-java/protocol-java-physics` | Java Edition physics kit: vanilla's `Entity` and `LivingEntity` movement over the world a player's client knows, bit for bit as the client of each version computes it (`EntityBehaviour` ranges, `EntityRules`, which picks the version's `Trigonometry` from `protocol-java-geometry`): the blocks' collisions and fluids (`world`: `BlockReader`, `Collisions`, `Fluids`), the entity (`entity`: `EntityBody`, `EntityMove`, `FluidState`, `BlockEffects`) and the living entity (`living`: `LivingBody`, `LivingSimulation`, `Travel`, `Jumping`, `Climbing`, attributes and effects). It knows nothing of players: the bodies' non-final methods and `LivingSimulation`'s `serverAiStep`, `travel` and `aiStep` are vanilla's override points, which a worker extension implements for its kind of entity as Movement's player layer does for `Player` and `LocalPlayer`; its test fixtures hold `TestWorld`, a test player's world built block by block |
| `anvil-player/player-protocol/protocol-java/protocol-java-packet` | Packet names of every supported protocol (`JavaPacketData`, `PacketRegistry`): minecraft-data's name of each packet a server sends, by connection state and packet ID, generated from the `protocol.json` that `packet-data.toml` pins per protocol (`pinPacketData`, `checkPacketDataReleases`); the names are minecraft-data's, such as `map_chunk`, never Mojang's |
| `anvil-player/player-protocol/protocol-java/protocol-java-item` | Item registry keys of every supported protocol (`JavaItemData`, `ItemRegistry`): the key of each item, such as `minecraft:emerald`, by the numeric ID inventory packets carry, generated like the packet names from the `items.json` that `item-data.toml` pins per protocol (`pinItemData`, `checkItemDataReleases`); minecraft-data's files before 1.19.4 leave out air, which the generator puts at ID 0 |
| `anvil-player/player-protocol/protocol-java/protocol-java-json` | `JavaProtocolJson`, the one JSON form of the Java protocol's values, which the MCProtocolLib worker codec and packet recordings share: packets by their `ClientboundPackets` kind, sealed models by their kinds, entity IDs as `{"entity": n}` and chunk sections by their encoding |
| `anvil-player/player-library/library-mcprotocol`                                  | Wiring bundle publishing the MCProtocolLib library: shaded `mcprotocol-common`, the client port and the client segments; owns `mcprotocol-releases.toml` and `pinLibraryReleases`  |
| `anvil-player/player-library/library-mcprotocol/mcprotocol-api`                   | Release-neutral MCProtocolLib client port that client segments implement for the worker shell, and, in `client.translation`, what their translations share: `PacketTranslation`, `PacketTable`, `EntityDataLayout`, the `PacketDemand` a session's listener answers and the `ConnectionLevel` a connection's translations share, and in its test fixtures the `TestClientListener` segment tests report to |
| `anvil-player/player-library/library-mcprotocol/mcprotocol-adventure`             | `ComponentTranslator`, which translates Adventure components to the protocol's `Text` through Adventure API stable since 4.0, for the segments of releases whose components are Adventure's; each chain extends it once as its `TextTranslator` with the accessors its releases' Adventure keeps differently, a later segment whose Adventure moved further replaces that subclass, and Adventure comes from the release's closure |
| `anvil-player/player-library/library-mcprotocol/mcprotocol-netty`                | `ReceivedFrames`, the netty handlers every client segment puts around its session's decoder (`codec`): one copies each frame's bytes while the player wants frames, the other translates the decoded packet and delivers both to the session's `ClientListener` on the channel's thread, so delivery follows the wire on every release; netty comes from the release's closure, as Adventure does for `mcprotocol-adventure` |
| `anvil-player/player-library/library-mcprotocol/mcprotocol-common`                | MCProtocolLib library provider, release data, worker host and segment selection, MCProtocolLib-free worker shell with its extension registry and typed transport, and private authentication store                                  |
| `anvil-player/player-library/library-mcprotocol/mcprotocol-java`                | Source-free side folder exporting one client segment per MCProtocolLib release whose client code differs                                                                            |
| `anvil-player/player-library/library-mcprotocol/mcprotocol-java/V*`             | Client segments `V1_16_5`, `V1_17_1`, `V1_18_2`, `V1_19_4`, `V1_20_6`, `V1_21_1`, `V1_21_11`, `V26_1_2` and `V26_3`, each implementing only `McProtocolClient` from its release key on through an adapter over its chain's `JavaClient` base; `V1_17_1` extends `V1_16_5`, `V1_19_4` extends `V1_18_2`, `V1_21_1` extends `V1_20_6`, `V1_21_11` extends `V1_21_1`, `V26_1_2` extends `V1_21_11` and `V26_3` extends `V26_1_2`, each replacing the base classes that changed |
| `anvil-process/process-api`                                         | Platform-provider/planning SPI, version data contract, artifact sources, distribution validation, and configuration contracts                                                      |
| `anvil-process/process-common`                                    | Platform declaration validation, version data reading, effective Java/topology requirements, forwarding negotiation, the split of a plan by process lifetime, artifact planning, and provider preparation/configuration      |
| `anvil-process/process-platform/platform-<name>`                      | One family per platform: its provider (`<name>-provider`) and, where it installs one, its platform agent assembly (`<name>-agent`); Paper and Spigot share `platform-bukkit` |
| `anvil-process/process-protocol/process-protocol-api`                                               | Shared operation descriptors, payloads, identities, types, and exceptions; no client/server contracts or core dependency                                                            |
| `anvil-process/process-protocol/protocol-host/protocol-host-api`                                 | Host-side clients, directories, connections, and artifact lookup; exports shared agent contracts without core or capability APIs                                                    |
| `anvil-process/process-protocol/protocol-agent/protocol-agent-api`                           | Embedded endpoint, native service, and operation provider contracts; exports only shared agent contracts                                                                            |
| `anvil-process/process-protocol/protocol-host/protocol-host-common`                                            | Host connections, stable process clients/directories, agent sessions, player observations, and artifact location                                                                    |
| `anvil-process/process-protocol/protocol-agent/protocol-agent-common`                                            | Embedded authenticated endpoint, native operation dispatch, extension loading, and the shaded agent artifact used by platform agents                                         |
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
| `anvil-testkit/fixtures`                                              | Independent consumer build for process, server-plugin, and extension fixture JARs                                                                                                   |
| `anvil-testkit/support`                                               | Host-side fixture artifact access and scoped extension loading                                                                                                                      |
| `examples/proof-of-patience`                                          | Standalone consumer example, not a home for framework system assertions                                                                                                             |
| `build-logic`                                                         | Shared conventions grouped by `module/`, `packaging/`, `adapter/`, `library/`, `platform/`, `capability/`, `integration/`, `fixture/`, `matrix/` (the live matrix and the tasks that write what it verifies into version and release data), `minecraftdata/` (the `module-minecraft-data` convention that makes a module generate one `MinecraftDataKind`, today the packet names, from the minecraft-data files its pins file pins: reading minecraft-data, the pins files, their pin task and protocol check, and the generator's arguments), `block/` (the `module-block-data` convention that generates `protocol-java-block`'s data from the block data read from vanilla clients: the generator's arguments and `checkBlockDataReleases`), `vanilla/` (the `vanilla-harness` convention of the standalone physics harness: `vanilla-clients.toml` and `pinVanillaClients`, the clients' preparation into `~/.anvil/vanilla-clients`, each family's `src/<family>/release-<minecraft>` sources, and `generateVanillaData`, which writes the traces and each release's block data), and `download/` (checksums and the HTTP reader the pin tasks share) |
| `build-logic/settings`                                                | Lean settings conventions loaded by every project's parent classloader: library registry, library repositories, and library and platform layout checks                             |
| `docs/content`                                                        | Task-oriented Scriptorium guides for using, extending, and contributing to Anvil                                                                                                    |
| `scriptorium.project.json`                                            | Scriptorium project metadata and version policy                                                                                                                                     |

## Dependency and extension rules

- `anvil-api` has no Anvil project dependency. `anvil-engine` depends only on `anvil-api`.
  Ordinary implementations and API modules consume only their own family's APIs and `anvil-api`;
  foreign API dependencies and re-exports are forbidden unless an ancestor-owned contract API is
  explicitly declared through `architecture.sharedApis`, or a sibling family's API through
  `architecture.dependsOnApis`, as `builtin-dialogs` does for `commands-api`. Each exposed API needs permission
  independently. Organizational grouping does not merge families; `capability-player-api` and
  `capability-process-api` declare `family = ":anvil-capability"` because `capability-common` composes both. The agent's host and agent families (`protocol-host`,
  `protocol-agent`) share only `process-protocol-api`; they must not depend on or re-export each other's API or
  implementation.
- Keep concrete platform SDKs, MCProtocolLib, serialization, transport implementations, and assembly
  modules out of `anvil-api`. Global declarations and engine/scenario registration belong in core;
  scoped services stay in their owning APIs even when another family needs them.
- Bind scoped services at the assembly boundary through consumer-owned inputs or preparation contracts.
  Keep those contracts focused on the required operation; do not copy a complete foreign API into
  a wrapper or pass Anvil family services through untyped service lookup.
- Implementations/adapters consume APIs and external libraries, not sibling implementations.
  Do not disguise source dependencies as external Maven coordinates or classify implementations as
  assemblies to evade verification. The worker kits `player-worker` and `protocol-java-worker` are API modules that
  carry behaviour by design: every protocol library, Anvil's or another project's, embeds them to run worker
  extensions, so they hold only what is the same for every library and nothing of a library's transport. The block
  data `protocol-java-block` and the physics kit `protocol-java-physics` are API modules that carry behaviour by design
  too: worker extensions use them, never a library, and they reach a worker's class path as runtime dependencies of
  the capability sides that use them, which the family root brings to the runtime and the worker's host entries carry.
  The launcher owns internal composition and packaging;
  `anvil-integration` is reserved for external integrations.
- Use `ServiceLoader` for the existing provider SPIs. Preserve service descriptors and merge them
  when shading. Do not add broad reflection scanning as an alternative discovery path.
- Each player selects its protocol library: its own `protocolLibrary`, else the scenario's, else the engine's,
  else the one installed library whose release supports its Minecraft version most strongly; a tie requires an
  explicit choice. A player created from a lease uses the leased account's library instead. Launcher composition
  creates one capability runtime per selected library.
- Public capabilities extend `PlayerCapability` or `ProcessCapability`, both rooted in the neutral
  `Capability` contract. Dependencies remain within the same owner. Neutral player providers use
  shared observations and dependencies. Protocol-backed providers declare what they need from the player's library,
  never the library itself: `requiredProtocols()`, which the library's `ProtocolLibraryProvider.protocols()` must
  contain, and `workerBacked()` for a worker extension without an edition protocol, such as Session's;
  `supportedLibraries()` is only for a capability that uses one library's own services. Agent process providers
  select supported platform IDs.
- Capabilities act on their owner's lifecycle only through `CapabilityContext.on(Class, Consumer)` with a sealed
  `CapabilityEvent` (`capability.api.event`), grouped by subject: `event.scenario` (`ScenarioStarted`,
  `ScenarioFinished`) and `event.lifecycle` (`ProcessRestarted`, `Closed`, which is also how a capability registers
  cleanup). `capability-common`'s `CapabilityEvents` dispatches them under one rule set: reverse
  registration order, every action runs, the first failure is reported with later ones suppressed, and an owner
  refuses an event it never reports. A new lifecycle moment becomes a new `CapabilityEvent` type plus the point
  that reports it, never a new context method or a separate action list.
- Retrieve only declared capability dependencies. The agent-backed `Players` capability must remain
  independent of `Session`.
- A built-in capability belongs to whoever can normally do or know the thing: what a client can do or see
  belongs to the simulated player (`PlayerCapability`), what a server or proxy can do or know belongs to the
  running process (`ProcessCapability`); a proxy counts as server side. The implementation (packets, agent
  events) never decides the owner.
- A player speaks its edition's protocol: `protocol-java-api`'s `JavaProtocol` (`listen` by packet class, `send`,
  `listenSent` for the packets bindings send, `listenFrames` for every packet as its frame, `want` for a costly packet's
  value, `supports`, `world`,
  `protocolNumber`), which a worker extension gets from
  `PlayerBindingContext.protocol(JavaProtocol.class)`. Packets are immutable values grouped by area under
  `packet.<area>`, an area of more than seven packets split by subject below it (`packet.world.border`,
  `packet.world.chunk`), one class per game packet in the game's shape; the protocol owns the data types they need
  (`model.<area>`, enums in `type`) and carries text flattened to plain strings, except chat, container titles and the
  names and lore of item stacks, which it carries as `player-api`'s `Text` (`protocol.api.model.text`): the component
  tree the server sent, whose `spans()` resolve what the player sees, translated by the segment's `TextTranslator`.
  Item stacks are translated by each segment's one item translator, which its adapter hands to every translation that
  reads stacks: the NBT `display` of the releases before 1.20.5 (`ItemTranslator` of `V1_16_5` and `V1_18_2`), the
  data components from 1.20.5 on (`V1_20_6`'s `ItemTranslator`, whose `ComponentItemTranslator` each segment whose
  components moved replaces).
  Where a packet holds NBT, such as a
  dialog or a registry entry, the protocol carries it as `model.nbt` tags (`NbtTag`, `NbtCompound`), never as SNBT
  text, and the feature interprets it: `dialogs-java` reads dialogs, flattens their text components itself and
  keeps custom payloads in the worker, where a click names them by handle. A listener follows the player's current
  connection. `JavaProtocol.world()` is the world the client knows, its `JavaWorld`, which the kit's dispatcher keeps
  from the packets it delivers: each packet applies whole under one lock before its listeners see it, a single read
  and a `read(...)` see one moment, and chunks are decoded and kept only once something asked for the world, the
  client's own player among them where its rules read chunks (its level's loading from 1.21.4, its ticks before 1.17).
  `JavaProtocol.player()` is the client's own player, its `LocalPlayer`, which the kit keeps for every Java player of
  every library: the position, view, velocity and ground state the client knows, the entity ID, game mode and death
  the server set, and the client's tick, which the library starts once the player's extensions are bound and closes
  with the player, 20 a second on one virtual thread per player that catches up at most ten ticks. The client applies
  each packet, and its listeners run, under one lock with the ticks, as the vanilla client runs both on its thread;
  `LocalPlayer.run` and `get` take that lock for an operation, and a feature that moves the player hooks its own tick
  into the client's through `LocalPlayer.onTick`, between the connection's tick and the tick end.
  Chunk decoding never disconnects: a column the release cannot read is left out with a warning.
  Every packet a player receives is also a `ClientboundFrame`: the connection's state, the packet's ID, its bytes after
  the ID and minecraft-data's name of it, with its value when the protocol models it. A library that sees the wire
  answers `JavaConnection.handsOverFrames()` and, while the dispatcher `wantsFrames()`, delivers each packet as its
  frame in wire order; while a frame listener exists every modelled packet is wanted except the costly ones
  `ClientboundPackets.isCostly` names (the chunk column), whose value is decoded, and carried by its frame, only while
  something wants its class: a listener, `JavaProtocol.want(Class)` or the world. A packet recording never keeps a
  costly value (`RecordedPackets`), and a `PacketMask` refuses to compare a costly class by value.
  MCProtocolLib sessions translate and deliver from the netty pipeline through `mcprotocol-netty`'s `ReceivedFrames`,
  never from session listeners, which releases call on other threads or out of order. Packet values name their kind
  through `ClientboundPackets` wherever they are written down, in `protocol-java-json`'s one JSON form; an entity's
  protocol ID is an `EntityId`, never an `int`, so whatever reads packets can tell entities from other numbers.
  Release-specific packet code lives only in the library's client segments: one `PacketTranslation` per area, listed
  by the segment's adapter in `mcprotocol-api`'s `PacketTable`, which alone refuses to send a packet the release
  cannot carry, with Adventure components translated by the segment's one `TextTranslator`, which extends
  `mcprotocol-adventure`'s `ComponentTranslator` and which the segment's adapter hands to every translation that reads
  text, and NBT translated by the segment's one `NbtTranslator`. Each session decodes through a table of its own, so
  the entity translation can remember the types of the session's entities: entity data is decoded by entity type from
  the release's `EntityDataLayout`, never by guessing from value types. The table is created for the session's
  listener, which is its `PacketDemand`: a translation of costly packets, such as `ChunkPackets`, decodes only what
  the player's dispatcher `wants`. Translations of one connection share what one reads and another needs through a
  per-table object, such as the `ConnectionLevel` that the connection translation records and the chunk translation
  sizes a column by. Release quirks stay in the segments: where releases say the
  same thing in different packets, every release reports the current packet (spawns as `ClientboundAddEntity`, the
  player's own inventory slots as `ClientboundSetPlayerInventory`, named sounds as `ClientboundSound`, the player's
  death as `ClientboundPlayerCombatKill`), and the
  current packet a feature sends reaches an older release in that release's form (`ServerboundAttack` as the attack
  action of the interact packet), such as `ServerboundAcceptTeleportation`, whose position and view only 26.3's
  carries. What the client answers about its own position belongs to the kit's client player, never to a segment or
  a feature: it accepts every teleport as the packet arrives, followed before 26.3 by the position it left the player
  at, takes the velocity of its own entity, ends each tick from 1.21.2 (`ServerboundClientTickEnd`) and from 1.21.4
  tells the server once it loaded its level (`ServerboundPlayerLoaded`), in the vanilla client's order. A library's
  `PlayerBindingContext.viewRotation()` is that player's view. Movement only adds physics: its tick moves the client's
  player, and its operations place and turn it. A release without a packet leaves it out of the table; a feature checks `supports`,
  or `JavaProtocol.require` throws `ProtocolUnavailableException` when it cannot work without some packets. Features
  never touch native sessions or library packets: they follow connections through
  `PlayerBindingContext.bindConnection` and close their listeners together through `Subscription.all`.
  The worker contracts belong to the player family: `player-api`'s `WorkerExtension`, `PlayerBindingContext`,
  `OperationRegistry` and `WorkerBinding` under `protocol.api.worker`, and the typed `ProtocolChannel` with
  `OperationDescriptor`/`EventDescriptor` that both ends of a player's channel share. Capabilities implement and
  call them directly; nothing mirrors them on the capability side or bridges them in the launcher. Every library's
  worker runs extensions through the kits: `WorkerExtensions` discovers them, enforces the registration phase,
  namespaced uniqueness, unavailability and cleanup, and `JavaPacketDispatcher` is the player's `JavaProtocol`. A
  library supplies its `JavaConnection`, its packet translation and its transport; MCProtocolLib's owns the typed
  encoding on both ends of its worker process (`WorkerMessageCodec`). The in-process fixture `test-java-library`
  runs the built-in capabilities on the kits alone.
  Never add feature branches or an opaque universal packet abstraction to the MCProtocol worker.
- Built-in families live below the group of their participant, decided by who can normally do or know it, never by
  how it is implemented: `capability-player/capability-player-builtin` for what a client can do or see
  (`PlayerCapability`, owned by the simulated player), `capability-process/capability-process-builtin` for what a
  server or proxy can do or know (`ProcessCapability`, owned by the running process; a proxy is server side).
- A built-in capability is a family folder `builtin-<feature>` whose root applies the `module-capability`
  convention and only wires its members into `builtin-<feature>`: `<feature>-api` (artifact `builtin-<feature>-api`)
  holds the API and, for capabilities with a worker side, the `.channel.<Feature>Channel` both sides share;
  `<feature>-common` (embedded, unpublished) holds the host provider; `<feature>-<edition>` (artifact
  `builtin-<feature>-<edition>`, such as `-java`) holds the edition's `WorkerExtension<Object>`, which keeps the
  empty default `libraryId()` and whose `nativeSessionType()` is `Object.class`. The editions are the `protocol-<edition>` folders below
  `anvil-player/player-protocol`. The root is a wiring bundle: consumers compile against its shaded JAR, and its
  sources and Javadoc JARs carry the embedded common code. The convention derives every artifact ID from the folder
  names and fails a build file that sets its own, and `gradle-capabilities` tests that each family has a plugin
  installing `builtin-<feature>`.
  Common code lives in the feature packages, never in `common` or `util` packages. An agent-backed provider,
  such as Dispatch's, lives in `<feature>-common` as well; the convention refuses code in every family root.
  A process family that needs a platform's own API beyond the core operations ships its own agent extension:
  `<feature>-agent` (artifact `builtin-<feature>-agent`, `module-api` with `module-java-legacy`) holds the
  `AgentOperation` descriptors and payloads, which the host provider turns into `ChannelOperation`s, and the
  platform-neutral handlers; each `<feature>-<platform>` (artifact `builtin-<feature>-<platform>`) answers them for
  one agent platform, a `platform-<name>` folder with a `<name>-agent`, against that platform's API only, embeds
  nothing and is compiled for the oldest Java its servers run on. The family root declares the agent APIs through
  `architecture.dependsOnApis` and brings the channel and every platform side to the runtime, where the provider's
  `agentExtensions()` finds their JARs by class: the channel's for every platform it serves and each side's for its
  own. Player families have no agent members. A platform whose servers rename what a side uses between supported
  releases gets a wiring-only side with one `release-<minecraft>` part per change (never `V*`, which names library
  segments), each compiled against the first release it serves and installed only where
  `AgentOperationProvider.supports` finds the server's Minecraft version in its range through `MinecraftRelease`. An
  extension needing what the release cannot carry throws `ProtocolUnavailableException`, and the worker reports the
  capability unavailable with that reason; a `LinkageError` or `ServiceConfigurationError` while binding likewise
  disables only that capability. An external capability that needs library code Anvil's protocol does not cover may
  still keep a segmented library side: its extension takes the port's adapter only through
  `PlayerBindingContext.adapter(Class)` while binding, never through `ServiceLoader` (`checkAdapterLookup`).
  Composition skips a protocol-backed provider, and its dependents, with the reason: when the library lacks one of
  its `requiredProtocols()`; for a player without a native worker, when it is worker-backed; and for a player with
  one, when the worker does not install its capability, with the worker's reason or "not installed by the <library>
  worker". A provider that cannot apply to a library, for its protocols or its worker, yields to an applicable
  provider of the same capability, such as a library's own Session. Host providers do not check installation
  themselves.
- `PlayerBindingContext` supplies an existing player's lifecycle, client version, edition protocol, native session
  generations and release adapters to a binding; it is not another player entity, and the library's worker player implements it. `PlayerConnectionEvent`
  and `ViewRotation` live under `protocol.api.model.player`, `OperationDescriptor<Q, R>` and `EventDescriptor<E>`
  under `protocol.api.model.channel`. View rotation holds yaw/pitch; a descriptor pairs an ID with its payload
  classes.
  Event payloads such as `PlayerConnectionEvent` remain separate values. Worker bindings own per-player
  state and listeners. Protocol bindings use `bindConnection` for state of one connection; only segmented code uses
  `bindNativeSession` for generation-scoped native listeners across reconnects, and old native handles must not
  silently bind to replacement sessions.
  Neutral player provider/context contracts live under `capability.api.player`. Protocol factories and their
  context, which hands a provider the player's `ProtocolChannel` and library services, live under
  `capability.protocol.api.player` in `capability-player-api`. Capability composition works on the player-api
  `ProtocolPlayer` itself. A player capability is backed by its
  player's protocol, never by a process's agent: what a server or proxy knows about a player belongs to that
  process's capability. Agent-backed process factories implement `AgentProcessCapabilityProvider` with
  `AgentProcessCapabilityContext` in `capability-process-api` under `.api.process`; these have process/platform
  identity and their own typed request channel, without player state, return shared `CapabilityDescriptor`
  values, and use inherited dependency/cleanup contexts. The mechanism API depends only on the shared
  capability API. All matching player provider kinds form one dependency graph before capability creation.
  Shared composition owns dependency ordering and cleanup, while launcher adapters bind request channels to
  agent clients. Capability APIs do not expose agent-family types.
- `RunningProcess` extends `CapabilityOwner<ProcessCapability>` and exposes capabilities directly.
  The logical process owns capability instances across JVM generations; global process contracts do
  not depend on agent APIs. Agent-backed implementations are created after initial transport readiness
  and close after players, before process/transport finalization. A restart replaces the transport
  while retaining capability instances; requests can be unavailable during replacement. Closed owners
  reject capability lookup and report `hasCapability` as false.
- A protocol library may expose its own stable services through `ProtocolPlayer.findService`.
  Anvil's own player contracts reach capabilities through `player-api` itself (`ProtocolChannel`, the worker
  contracts), and process services through launcher adapters, never through `findService` or a fake universal
  packet abstraction.
- Authentication is an optional `ProtocolLibraryProvider` service. Tooling depends on that API, not
  MCProtocol's account-store implementation. Account files are managed outside project configuration.
  The IntelliJ account manager and Gradle `anvilLogin`/`anvilLogout` share the `AnvilAuthentication` entry point.
  Named pools live in the account directory's versioned `pools.properties`, documented on
  `AccountManager.pool(String)`; the runtime reads it and the IDE writes it, with the same rules.
- External embedded-agent handlers implement `agent.server.api.operation.AgentOperationProvider`
  and own their operation namespace. `install(platform, registry)` receives the platform agent, so a handler
  reaches the native API through `requireService` and can register native listeners when the endpoint starts.
  Install their JARs under `plugins/anvil-agent-extensions`; do not bundle Anvil agent APIs or
  platform SDKs into those JARs. An agent-backed capability provider that needs its own extension declares it
  through `agentExtensions()` (platform IDs and a class the JAR contains); launcher assembly locates each JAR
  on the runtime class path and platform planning installs it into every process of those platforms. Host
  capabilities receive scoped request channels; launcher adapters bind them to connections obtained through
  `AgentDirectory`.
- Every platform agent provides the core operations once: `ping`, `command` (`PlatformAgent.executeCommand`) and
  `player-events`, the players' joins and leaves, which the agent reports to `AgentServer.playerEvents()` (`PlayerEvents`).
  Keep that vocabulary small: only facts about players' connections and the console. Built-in capabilities use
  them through `ProcessCapabilityContext.platform()`, which carries only that process's events; anything else a
  capability needs from a platform belongs to an agent extension, which a built-in process family ships as its
  `-agent` and `-<platform>` members. Never send a platform action through `command` to avoid an extension.
- Platform agents expose native services and their scheduling rules. The endpoint owns its extension
  class loader; the scenario owns host connections.
- A workbench holds a scenario's machine side; the host keeps planning and managed execution. The launcher builds
  one `LocalWorkbench` from installed and supplied execution providers and takes its bound execution providers,
  artifacts, Java and workspaces from it, never from the environment's implementations directly. `DefaultScenarioFactory.create`
  checks `AnvilScenario.requirements`, and the execution providers its processes imply, through
  `RequirementCheck` before planning; an unmet requirement throws `ScenarioRequirementsException` with every reason,
  which JUnit turns into a skip unless `anvil.requirements=strict`. An execution provider reports why it cannot run
  processes through `unavailableReason()`; Docker's pings its daemon.
- A process's workspace is reached through `ProcessFiles` (`RunningProcess.files()`, `PlatformContext.getFiles()`,
  `PreparedProcess.files()`), never through a local path, because its workbench may keep it on another machine.
  Platform providers write configuration only through it; `WorkspaceProvisioner.files(workspace)` opens the local
  implementation, `WorkspaceProcessFiles`, which confines paths as scenario-declared paths are. `PlatformContext`
  still carries `workDirectory` for installations the machine links into the workspace, such as NeoForge's, so
  NeoForge runs only on the local workbench.
- Every path a machine-side contract takes or returns (execution, provisioning, `PlatformArtifactSource`,
  `PlatformContext.cacheDirectory`, `JavaCommand.jar`, workspace assets) is the workbench's. A file of the engine's
  machine reaches it only through `Workbench.store` (`PlatformArtifactSource.store` for providers): the scenario's
  preparation stores every path asset, providers store local distribution JARs, and layouts and caches are rooted
  at `Workbench.workDirectory()` and `cacheDirectory()`, never at the engine options' directories. The host never
  opens a workbench path.
- Protocol libraries start their worker JVMs through `ProtocolLibraryContext.getWorkers()` (`ProtocolWorkerLauncher`),
  never with their own `ProcessBuilder`: the launcher binds it to `Workbench.launch`, which runs the worker where the
  scenario's processes run, so players' game traffic stays on the workbench. A `WorkerLaunch` keeps the library's
  closure, resolved through the context's artifacts on the workbench, apart from the engine's own class path
  entries, which the launcher stores first. The local workbench runs workers on the engine's own Java runtime.
- A process's addresses mean the same on the engine's machine and on its workbench: a workbench on another machine
  makes every address it hands out reachable at the same host and port on the engine's loopback, so agent
  connections, readiness probes and tests connect as they do locally.
- The launcher chooses the workbench through `WorkbenchChoice` (`anvil.workbench`, `ANVIL_WORKBENCH`, the pairings'
  default, else local) and fails when the chosen one is unreachable, never falling back. `RemoteWorkbench` mirrors
  each loopback address of a remote target in `RemoteProcessTarget` and carries connections through `TUNNEL`
  streams; `Workbench.expose` carries a workbench process's connections to an engine service back through
  `REVERSE` streams. A workbench on another machine reports `local = false`, and `RequirementCheck` then refuses
  NeoForge and Java installations named by an engine path. Every remote handle is released when its engine's
  control connection ends.
- An `ExecutionContext` is a scenario's data only (priority, offline, refresh, network policy); the machine's
  services (cache, runtimes, image locks, ports) are `ExecutionServices`, which a workbench binds to each
  `ExecutionProvider` and hands out as a `BoundExecutionProvider`, so a context crosses the wire and services never
  do.
- Every request of the workbench protocol is a `WorkbenchRequest<R>` model under `protocol.model.<subject>` naming
  its answer type; each subject is a sealed interface listed in `WireMapper`, and the server dispatches each with an
  exhaustive switch (`EngineOperations`, `ProvisioningOperations`, `ExecutionOperations`), never on strings.
  Connection headers, answers and server messages are sealed the same way, and a failure crosses as a `WireFailure`
  kind with structured members, never as a class name. Files the workbench keeps (pairings, server identity) are
  typed models, never maps.
- `anvil.debug` names processes whose JVMs accept a debugger: platform planning (`ProcessDebugging`) gives each a
  `debug` listener and a JDWP agent bound to it, so the port is reserved, published and mirrored like any listener's,
  and `DebuggerAnnouncement` prints where to attach.
- `executionProviderId` selects providers in declarations and execution plans; `RunningProcess.executionId()`
  identifies one execution attempt. Engine `ProcessTimeouts` defaults are overridden independently by scenario
  startup/shutdown values. Engine `ProcessScheduling` parallelism and startup memory govern each scenario operation,
  not aggregate engine usage; its `processors` becomes each process's `-XX:ActiveProcessorCount` and its
  `priority` reaches the execution provider, which applies it or refuses the environment.
  Keep artifact download concurrency separate.
- `JavaSelection` groups Java requirement and source in engine, scenario, and process declarations. Omitted members
  inherit independently; an explicit empty requirement overrides an inherited version and selects the platform's
  preferred LTS, unless a declared local source (home or executable) names a JDK: planning then runs that JDK's
  version, which it reads through `PlatformJavaSource` (bound by the launcher to `JavaProvisioner.inspect`), and
  accepts or refuses it like a request. `JavaRequirementPlanner` supplies execution a selection with an exact feature
  version. Scenario listener binding and LAN permission belong to `NetworkPolicy`, alongside exposure and access
  policy.
- Processes run on LTS releases only (11, 17, 21, 25, then every fourth); Java 8 is not one of them.
  Each provider owns `<platform>-versions.toml` (Java rows, `[agent] minimumJava` and known versions; the
  build adds the verified ones) and exposes it through `PlatformProvider.versionData()`; process-common reads it, so
  process-api carries no parser. The agent minimum raises the effective minimum and the default LTS;
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
  another family's agent. VERIFIED means run by the live matrix, `live-matrix.toml` at the repository root, the only
  list of verified combinations: one table per platform, named by the provider's id, whose rows name a version and
  the Java the live suites run it on. The build writes them into the shipped version data (`writeVersionData` of
  `module-platform-provider`, and `writeReleaseData` for a protocol library's releases, which are verified on every
  Minecraft version a matrix server runs); hand-written verified data fails the build. `CompatibilityScenarioFactory`
  requests each row's Java, and `CompatibilityScenarioFactoryTest` (the server suite's `matrixTest`, in every build)
  plans its scenarios against the written data. Every other supported version is only known (COMPATIBLE). Docs keep one Java-per-version table in the Java provisioning guide,
  which `JavaVersionTableDocumentationTest` compares with the installed providers' version data and the
  planner's defaults.
- Java requirements are execution-agnostic. Local homes and verified archives belong to Java provisioning;
  Docker image mappings belong to the Docker execution provider.
- A scenario's processes are servers, proxies and containers. A container runs an image instead of a platform:
  it goes through no platform planning and has no agent or capabilities, but shares the managed lifecycle, the
  console, readiness and failure actions with the others. Targets start a `ProcessLaunch`, a `JavaCommand` or an
  `ImageLaunch`, and a provider refuses what it cannot run with the provider to use instead.
- Each process may name its execution provider; the managed group opens one session per provider in use. A process
  reaches another inside their environment when one session runs both, else at the other's host address.
- Execution providers own network topology and endpoint translation. A local loopback bind is host exposure
  control, not process isolation; strict proxy-only isolation is rejected unless the provider can enforce it.
- Execution sessions reserve host listener ports through the engine's `PortReservations` and release them when
  they close, the ports of a process's declared listeners included; a target names where peers reach each
  listener (`peerListeners`) at the host of its `peerAddress`, and `ProcessAddresses` fills the
  `${anvil.process...}` placeholders from them. Managed execution's `PortSelection` picks them below the ephemeral ranges, because a process
  binds its port seconds after preparation selected it; do not probe port 0 in a provider.
- A simulated player's name is its unique identity within the scenario; its Minecraft username defaults to
  the name and may be shared by several players. The client records the profile its login success granted,
  which a proxy or login plugin may have renamed, and `SimulatedPlayer.grantedProfiles()` keeps every profile
  granted at its logins. A process's Players capability finds a player by those UUIDs in its own process's
  events and never looks a player up by username, which platforms match without regard to letter case.
  A proxy's `backend(player)` names a scenario process.
- `api.process.RunningProcess` exposes lifecycle, address, and console; server/proxy specializations live in
  `api.process.type`. Core keeps only what the client knows itself: `SimulatedPlayer.requestedProfile()` and
  `grantedProfile()`. What a server or proxy knows about a player, such as the profile it sees and the backend a
  proxy connected it to, belongs to that process's `Players` capability.
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
  `exception`, and `channel` are exempt: every capability with a worker side keeps its host-to-worker contract in
  `.channel`, even when it is the package's only file. A segment's version package (`.v1_18_2`) and an edition
  side's wiring package (`<feature>.java`) hold one class by design and are exempt too. Avoid catch-all
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
- Local/named server artifacts declare `minecraftVersion`. Native clients must match every reachable
  server. Preserve explicit client overrides and deterministic native selection; no implicit ViaVersion fallback.
- Prepare declared assets and caches before provider configuration. Runtime ports, forwarding,
  agent credentials, and EULA values take precedence. Independent preparation and startup work may run
  concurrently within configured limits. Configure each process during its start, including the first
  start and every restart; independent configurations may overlap and must use the supplied process
  workspace and planned shared values. Preparation owns reusable inputs, not launch resources.
  `PreparedWorkspace.refresh(WorkspaceRefresh)` runs before every generation: preparation installed the assets for
  the first one, and each later generation installs them again, so `ALWAYS` assets come from their current sources
  and a missing `SEED_ONCE` asset is reseeded. Caches restore and cleanups run only at preparation and finalization.
  `WorkspaceRefresh` is a typed model with one member per thing a generation may need that changed since
  preparation; today its `AssetRefresh` entries name each file asset's current source by target, which the launcher
  stores through `Workbench.store` again, so a file rebuilt on the engine's machine reaches a workbench on another
  machine. Add a member for a new need rather than another method or workbench request.
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
  `process` and `provisioning`.
  Behaviour that needs only `anvil-api` belongs in the engine and behaviour over one family's types in that family:
  capability discovery in `capability-common` (`ProcessCapabilityRuntime.discover`, `LibraryCapabilityRuntimes`), the
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
  for a new protocol (version, module, protocol, Minecraft versions, Java, features), and
  pin its closure with `pinLibraryReleases`. A new protocol gets its block data read from its vanilla client: a
  `vanilla-clients.toml` row in the physics harness, pinned with `pinVanillaClients`, then `generateVanillaData`,
  which writes `protocol-java-block`'s `vanilla/<protocol>.blocks` (`checkBlockDataReleases` fails without it), a
  `packet-data.toml` row in `protocol-java-packet`, pinned with `pinPacketData`, and an `item-data.toml` row in
  `protocol-java-item`, pinned with `pinItemData`; a version of a protocol that has them needs none. Add a segment only
  to a side whose newest segment fails
  `checkSegmentLinkage` against the new closure, named after the new release key; a segment folder must name
  a release key, so raising a key renames that segment. A new segment extends the side's newest one
  (`segment { extends(...) }`) and names each base class it brings its own version of, or leaves out because
  the release needs none, in `replaces("<binary name>")`, which the build validates; code a chain shares, its public `JavaClient` base and abstract bases of
  translations whose bulk does not change, lives in the chain's earliest segment, and each segment has its own
  `McProtocolClientAdapter`. Add the release to `McProtocolWorkerContractTest`,
  platform `known` versions and `[[java]]` rows only where Java changes, and add a `live-matrix.toml` row for each
  combination the live suites should run, which makes it verified; every other listed version is COMPATIBLE, and user-supplied release
  data is UNTESTED. Update the versions page, the Java table, the README, and the FAQ. The worker shell never
  imports MCProtocolLib; release-specific client code lives only in `mcprotocol-java` segments, whose
  `linkage.txt` requirement keywords the worker's self-check enforces. A release with an unpinned closure is
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
- A worker extension's tests bind it to the kits' test players, never to a fake of their own: `TestJavaPlayer`
  (`protocol-java-worker` test fixtures) speaks the real `JavaPacketDispatcher`, so the test delivers packets, reads
  what the player sent, connects and disconnects it as a worker does and runs its client's ticks one by one with
  `tick()`, since the client does not tick on its own until the test starts it, and `TestOperations` calls what the
  extension registered. A host capability's tests talk to `player-api`'s `TestProtocolChannel`, which answers operations,
  publishes events, records requests, payloads, subscriptions and waits, and scripts what happens while the capability
  waits; a provider's tests create it in `capability-player-api`'s `TestProtocolPlayerCapabilityContext`, which hands
  it that channel and the capabilities the test offers as its dependencies. A client segment's tests take what its
  sessions report through `mcprotocol-api`'s `TestClientListener`. No test implements a contract that has a
  shared test double itself; `checkSharedTestDoubles` of `module-defaults`, part of every module's `check`, fails a
  test class that does and names the fixture to use. Only the modules that own a contract or its fixture, which the
  fixture cannot serve, test it with an implementation of their own. A module shares test fixtures through the `module-test-fixtures` convention, which keeps
  them out of its publication.
- Tests that need a real Docker engine live in their module's `dockerTest` JVM test suite (`src/dockerTest/java`),
  so `test` and `build` need no Docker. They run in the image the `anvil.docker.testImage` property of
  `gradle.properties` names, the only place it is named, passed to them through an argument provider: a copy of
  `eclipse-temurin:21-jdk` on the GitHub Container Registry, which `docker-test-image.yml` refreshes when `dev`
  changes the reference, weekly and on demand. CI pulls it with its `GITHUB_TOKEN`, never anonymously from Docker
  Hub or ECR, whose limits shared runners exhaust, and a test pulls it only when the engine lacks it.
- `anvil-testkit/tests/runtime/src/test`: cross-module discovery/composition without live Minecraft.
- `anvil-testkit/tests/server/src/test`: real servers in four suites, one package and task each, all requiring
  `-Panvil.testMode=full`. `release` runs one test class per capability, annotated `@Release`, against the direct
  Paper server of the release `releaseTest-<minecraft>` names; the server is kept for the task's JVM, each test
  joins its own player through `FixtureServer`, and capabilities a release predates are gated with
  `TestedRelease.isAtLeast`. `physics` (`physicsTest-<minecraft>`, `physicsTest` for all) runs every physics course
  with client physics on the same server, described below. `platform` (`platformTest`, `-PanvilPlatforms`) runs
  Spigot, the proxies and NeoForge, and `behavior` (`behaviorTest`) lifecycles, sessions, routing, tooling and
  extensions, which pick scenarios through typed `CompatibilityScenarioFactory` lookups, never by name. `test` runs
  all four. The live matrix is the data file `live-matrix.toml` at the repository root: the build registers one
  release task and one physics task per `[[paper]]` row and CI one `Release <minecraft>` and one `Physics <minecraft>`
  job per release. Only `matrixTest`, which checks `CompatibilityScenarioFactoryTest`, and `blockDataTest`, which
  requires block data in `JavaBlockData.shipped()` for the protocol of every `[[paper]]` row, run in every build;
  neither starts a server, and no test hard-codes a release without block data.
- Client physics is checked against the vanilla client itself. `anvil-testkit/physics/courses/<course>.toml` declares
  each course: the blocks laid in order, the player placed at rest at its `[start]` or joining as over the network
  (`[join]`: the login, the first position packet with teleport ID 1, then the course's chunks after
  `chunksAfterTicks`), the keys held from each tick, the server's packets before given ticks (`[[server]]`, such as
  teleports, relative, absolute or, from 1.21.2, with a velocity), the first release it runs on (`since`) and the
  ticks it runs. `anvil-testkit/physics/traces/<minecraft>/<course>.trace` holds, for every supported release, the
  vanilla client's state after each tick and the packets it sent during it, in order, a join's handshake as tick
  `-1`, each packet by its `protocol-java-api` name with only the fields the release's own packet carries, from the
  harness's explicit list of the packets movement concerns. `ReferenceTraceTest` drives Anvil's client through the
  same join, keys and server packets and must reproduce both bit for bit. A maintainer who owns Minecraft generates
  the traces locally with the manual `generateVanillaData` task of the standalone `anvil-testkit/physics/harness`
  build (`./gradlew -p anvil-testkit/physics/harness generateVanillaData`), which downloads each client JAR and its
  mappings from Mojang by the SHA-1 that `vanilla-clients.toml` pins into a cache under `~/.anvil` and runs them
  headless, joining through the client's real packet handlers; the same run writes each release's block data into
  `protocol-java-block`'s `vanilla/`. A harness family whose releases name members differently keeps each release's
  own code in `src/<family>/release-<minecraft>/java`, compiled against that client alone, never reflection. The
  harness reaches non-public client members only through access widening: each family's and each release's
  `accesstransformer.cfg` (NeoForge's format, one class or member per line with its descriptor) lists exactly what
  the client preparation widens into `client-widened.jar`, deterministically and keyed by the lists' content, and
  `Unconstructed` alone allocates the objects the harness builds without their constructors. Only the traces and the
  block data are committed: never commit or publish Mojang's client JAR, its mappings, remapped or widened JARs or
  decompiled sources, and CI never downloads the client. The live `physics` suite runs the same courses on the Paper
  server of each release, those the release runs that script no server packet: `CourseTest`, through the suite's one
  course model `Course`, clears a site and lays the course with the fixture plugin's `anvil-blocks` command (never
  on the terrain of a random seed), places a new player at the start or the join's position, opens a `ConsoleWatch`
  and a `PacketTap` on `ClientboundPlayerPosition` before physics turns on, holds the keys tick by tick, and fails
  on any position correction or a "moved too quickly" or "moved wrongly" warning. No third-party plugin, such as an
  anticheat, joins the suite; the server runs Paper and Anvil's fixture plugin only.
- The independent `anvil-test-fixtures` consumer build under `anvil-testkit/fixtures` contains
  `test-process`, `test-server-plugin`, `test-extension`, and `test-java-library`. They use `src/main`; dependencies on Anvil
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
./gradlew :anvil-process:process-common:test
./gradlew :anvil-player:player-common:test
./gradlew :anvil-process:process-protocol:protocol-host:protocol-host-common:test
./gradlew :anvil-process:process-protocol:protocol-agent:protocol-agent-common:test
./gradlew :anvil-capability:capability-common:test
./gradlew :anvil-player:player-library:library-mcprotocol:mcprotocol-common:test
./gradlew :anvil-integration:integration-gradle:gradle-plugin:test :anvil-integration:integration-gradle:gradle-capabilities:test :anvil-integration:integration-gradle:gradle-platforms:test
./gradlew :anvil-testkit:tests:runtime:test
./gradlew :anvil-testkit:tests:server:matrixTest :anvil-testkit:tests:server:blockDataTest
./gradlew :build-logic:check :build-logic-settings:check
./gradlew build
./gradlew dockerTest
~~~

For protocol behavior, run the exact worker contracts, which cover every release in
`mcprotocol-releases.toml`, and full live coverage. For kicking/reconnects/identities, include
`PlayerIdentityReconnectSystemTest` for both Paper versions it runs. For platform providers/forwarding, run
every affected direct/proxy combination. Inspect retained `anvil-console.log` files after startup or routing
failures.

~~~shell
./gradlew test -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:releaseTest-1.21.11 -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:physicsTest-1.21.11 -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:platformTest -Panvil.testMode=full -PanvilPlatforms=spigot
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
  and call `reusable-verify.yml`, `reusable-docker.yml`, `reusable-intellij.yml`, `reusable-live.yml` and `reusable-publish.yml`; shared steps come from `whereareiam/devops`, and
  only Anvil-specific ones live in `.github/actions`. Pull requests automatically get the metadata check,
  the quick `build` checks, which start no server, and the Docker engine tests (`reusable-docker.yml`); the development
  and release workflows run the same verification before they publish. Full verification with the IntelliJ plugin, standalone
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
  `Spigot`, `NeoForge`, `Velocity`, `BungeeCord`. Add an area only for something readers recognise by that
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
- The console classifies output with `console.ConsoleLevel`: a line's declared level, its continuation lines
  inherit the previous level, and standard error counts as an error. `ConsoleBuffer` keeps each entry's level,
  so `ConsolePresentation` replays the retained tail when the level filter changes and navigates between
  error lines; `ConsoleActions` builds the console's toolbar. Filtering never drops retained output.
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
  A replacement waits for the previous launch's cleanup. `ToolingLaunch.rebuild` reruns the source's preparation
  beside a ready runner so its artifacts are rebuilt, and `ToolingConnection` owns that one rebuild process
  and terminates it on stop; `EnvironmentSession.rebuildAndRestart` restarts the process only after the
  build succeeded. An `EnvironmentSession` stays bound to its
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
  which owns both directories and global participation), and `view` (the account window).
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
