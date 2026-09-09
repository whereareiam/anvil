# AGENTS.md

## Purpose and scope

Anvil is a Java 21 testing framework for local Minecraft Java Edition processes and native-protocol
players. Preserve reproducibility, protocol fidelity, complete cleanup, and useful failure diagnostics.
Kubernetes, SSH/hosted orchestration, Fabric, Sponge, Bedrock, rendering, pathfinding,
autonomous AI, and crafting automation are outside the current project scope.

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
  `McProtocolMovementAdapter`, `SpigotDistributionResolver`, and `WorkspacePlanValidator`.

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
| `anvil-launcher`                                                      | Public default builder, property decoding, scoped-service adapters, default ScenarioExecutor/per-run assembly, native worker bridge, and shaded packaging                           |
| `anvil-capability/capability-api`                                     | Owner-neutral composition, typed requests/handler registration, and neutral player identity/observations/dependency/lifetime contracts                                              |
| `anvil-capability/capability-protocol-api`                            | Protocol-backed player providers/contexts, channels/events, and native worker contracts                                                                                             |
| `anvil-capability/capability-agent-api`                               | Agent-backed process/player providers and scoped request-channel contexts; shared capability API only                                                                               |
| `anvil-capability`                                                    | Shared dependency validation/composition/cleanup for capability owners, process/player facades, agent-provider adaptation, codecs, and worker bindings                              |
| `anvil-capability/capability-builtin/player/<feature>`                | Player feature API, native implementation using its own API and SDK, and provider/worker wiring                                                                                     |
| `anvil-capability/capability-builtin/agent/<feature>`                 | Process feature API and agent-backed wiring, independent of simulated players                                                                                                       |
| `anvil-protocol/protocol-api`                                         | Backend providers, player creation/composition, protocol-owned channels/workers, pinned runtime resolution, and optional authentication                                             |
| `anvil-protocol`                                                      | Player registration, version selection, authentication compatibility, composition, and observations                                                                                 |
| `anvil-protocol/protocol-mcprotocol`                                  | MCProtocolLib backend, pinned catalog, native workers, typed-channel transport, and private authentication store                                                                    |
| `anvil-platform/platform-api`                                         | Platform-provider/planning SPI, artifact sources, distribution validation, and configuration contracts                                                                              |
| `anvil-platform/platform-planning`                                    | Platform declaration validation, effective Java/topology requirements, forwarding negotiation, artifact planning, and provider preparation/configuration                            |
| `anvil-platform/platform-*`                                           | Provider-specific distribution/configuration implementations and platform-agent assemblies                                                                                          |
| `anvil-agent/agent-api`                                               | Shared operation descriptors, payloads, identities, types, and exceptions; no client/server contracts or core dependency                                                            |
| `anvil-agent/agent-client/client-api`                                 | Host-side clients, directories, connections, and artifact lookup; exports shared agent contracts without core or capability APIs                                                    |
| `anvil-agent/agent-server/server-api`                                 | Embedded endpoint, native service, and operation provider contracts; exports only shared agent contracts                                                                            |
| `anvil-agent/agent-client`                                            | Host connections, stable process clients/directories, agent sessions, player observations, and artifact location                                                                    |
| `anvil-agent/agent-server`                                            | Embedded authenticated endpoint, native channelOperation dispatch, extension loading, and the shaded agent artifact used by platform agents                                         |
| `anvil-integration/junit/extension`                                   | JUnit annotations, context injection, and lifecycle integration                                                                                                                     |
| `anvil-integration/junit/gradle`                                      | Optional anvilTest task and JUnit dependency wiring                                                                                                                                 |
| `anvil-tooling/tooling-runner`                                        | Foreground scenario shell, independent of Gradle APIs                                                                                                                               |
| `anvil-tooling/gradle/scenarios`                                      | Gradle DSL, src/anvil, artifacts, scenarios, authentication tasks, and unit-plugin registry                                                                                         |
| `anvil-tooling/gradle/bundle`                                         | Combined Gradle plugin and curated capability/platform unit plugins                                                                                                                 |
| `anvil-testkit/tests`                                                 | Cross-module runtime and live assertions                                                                                                                                            |
| `anvil-testkit/fixtures`                                              | Independent consumer build for process, server-plugin, and extension fixture JARs                                                                                                   |
| `anvil-testkit/support`                                               | Host-side fixture artifact access and scoped extension loading                                                                                                                      |
| `examples/proof-of-patience`                                          | Standalone consumer example, not a home for framework system assertions                                                                                                             |
| `build-logic`                                                         | Shared Java, testing, assembly, and publication conventions                                                                                                                         |
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
  Keep those contracts focused on the required channelOperation; do not copy a complete foreign API into
  a wrapper or pass Anvil family services through untyped service lookup.
- Implementations/adapters consume APIs and external libraries, not sibling implementations.
  Do not disguise source dependencies as external Maven coordinates or classify implementations as
  assemblies to evade verification. The launcher owns internal composition and packaging;
  `anvil-integration` is reserved for external integrations.
- Use `ServiceLoader` for the existing provider SPIs. Preserve service descriptors and merge them
  when shading. Do not add broad reflection scanning as an alternative discovery path.
- Launcher composition selects one protocol provider before capability discovery. Automatic selection uses
  the sole installed provider; ambiguity requires an explicit ID.
- Public capabilities extend `PlayerCapability` or `ProcessCapability`, both rooted in the neutral
  `Capability` contract. Dependencies remain within the same owner. Neutral player providers use
  shared observations and dependencies. Protocol-backed providers declare protocol support through
  `supportedProtocolIds()`; agent process providers select supported platform IDs.
- Retrieve only declared capability dependencies. The agent-backed `Server` capability must remain
  independent of `Session`.
- Packet behavior belongs in feature-owned native implementations. Feature wiring registers scoped
  typed channelOperation/channelEvent descriptors and `WorkerExtension<B>` bindings to the actual external SDK.
  Shared `OperationRegistry` accepts typed handlers. `WorkerCapabilities` enforces registration phase,
  namespaced uniqueness, and cleanup; launcher `ProtocolOperationRegistry` binds handlers to
  protocol-owned native operations; capability codecs own typed handler wire encoding/decoding.
  Never add feature branches or an opaque universal packet abstraction to the MCProtocol worker.
- `PlayerBindingContext` supplies an existing player's native SDK/session and lifecycle to a binding;
  it is not another player entity. `PlayerConnectionEvent` lives under `.api.model.player`, while
  `ViewRotation` and `EventDescriptor<E>` live directly under `.api.model`. View rotation holds yaw/pitch;
  an event descriptor pairs an event ID with its payload class.
  Event payloads such as `PlayerConnectionEvent` remain separate values. Worker bindings own per-player
  state and native listeners. Use
  `bindBackend` for generation-scoped
  listeners across reconnects; old native handles must not silently bind to replacement sessions.
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
- External backends expose their own stable execution-service contracts through `ProtocolPlayer`.
  Anvil protocol/capability services cross their boundary through explicit assembly bridges, not
  `findService` or a fake universal packet abstraction.
- Authentication is an optional `ProtocolProvider` service. Tooling depends on that API, not
  MCProtocol's account-store implementation. The Gradle account option is `--auth-profile`.
- External embedded-agent handlers implement `agent.server.api.operation.AgentOperationProvider`
  and own their channelOperation namespace.
  Install their JARs under `plugins/anvil-agent-extensions`; do not bundle Anvil agent APIs or
  platform SDKs into those JARs. Host capabilities receive scoped request channels; launcher adapters
  bind them to connections obtained through `AgentDirectory`.
- Platform agents expose native services and their scheduling rules. The endpoint owns its extension
  class loader; the scenario owns host connections.
- Java requirements are execution-agnostic. Local homes and verified archives belong to Java provisioning;
  Docker image mappings belong to the Docker execution provider.
- Execution providers own network topology and endpoint translation. A local loopback bind is host exposure
  control, not process isolation; strict proxy-only isolation is rejected unless the provider can enforce it.
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
  one implementation. Avoid catch-all `util` or `common` packages.
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
- Keep platform units explicit. The umbrella plugin supplies the curated built-in capability set;
  smaller installations choose capability unit plugins.
- EULA acceptance uses `anvil { acceptEula() }`.

## Runtime and reproducibility requirements

- Validate scenarios, EULA, distribution selectors, native versions, capabilities, authentication,
  Java, and forwarding before launching processes.
- Automated distributions pin a build or content checksum. `latest` is manual-only.
  Spigot uses GetBukkit prebuilt JARs with `Distribution.pinned(version, sha256)`; do not restore
  BuildTools execution or silently replace a pin.
- Local/named server artifacts declare `minecraftVersion`. Native clients must match every reachable
  server. Preserve explicit client overrides and deterministic native selection; no implicit ViaVersion fallback.
- Prepare declared assets and caches before provider configuration. Runtime ports, forwarding,
  agent credentials, and EULA values take precedence. Independent preparation and startup work may run
  concurrently within configured limits; provider configuration remains deterministic.
- Agents bind to loopback and authenticate with per-run random tokens. Non-loopback game listeners
  require a manual scenario and explicit LAN opt-in.
- Global `ScenarioExecutor` returns a ready context before engine extensions and setup run.
  Use `ScenarioContext.finish(boolean)` and `ScenarioAttachment.finish(boolean)` to propagate caller
  outcomes; default `close()` means normal completion and does not erase earlier lifecycle failures.
- Start servers before proxies; stop processes in reverse dependency order. Cleanup attempts every resource,
  preserves failures, and retains diagnostic workspaces and bounded output tails when a run fails.
- Never put online access/refresh tokens in Gradle inputs, CLI arguments, environment variables,
  system properties, logs, or project workspaces. Workers receive them only through private stdin.
  Agent session tokens are separate per-run credentials used by the managed child process.
- Use `AnvilScenarioDefinition` for a type-selected JUnit environment and `AnvilScenarioProvider`
  for catalogs, discovery, matrices, and interactive groups.
- Adding a supported MCProtocol version requires exact artifact URL/SHA-256, protocol number,
  Java requirement, binding family, worker/capability contracts, all supported direct/proxy routes,
  and supported-version documentation updates.

## Tests and verification

- Unit and focused integration tests stay in their owning module's `src/test`, normally mirroring
  the production package. Test observable contracts rather than duplicating implementation details.
- `anvil-testkit/tests/runtime/src/test`: cross-module discovery/composition without live Minecraft.
- `anvil-testkit/tests/server/src/test`: real sessions, capabilities, routes, and external agents,
  grouped by behavior. The whole task requires `-Panvil.testMode=full`.
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
./gradlew :anvil-engine:test
./gradlew :anvil-environment:execution:execution-managed:test
./gradlew :anvil-platform:platform-planning:test
./gradlew :anvil-protocol:test
./gradlew :anvil-agent:agent-client:test
./gradlew :anvil-agent:agent-server:test
./gradlew :anvil-capability:test
./gradlew :anvil-protocol:protocol-mcprotocol:test
./gradlew :anvil-tooling:gradle:scenarios:test :anvil-tooling:gradle:bundle:test
./gradlew :anvil-testkit:tests:runtime:test
./gradlew build
~~~

For protocol behavior, run exact worker contracts for both catalog versions and full live coverage.
For kicking/reconnects/identities, include `PlayerIdentityReconnectSystemTest` for both versions.
For providers/forwarding, run every affected direct/proxy combination. Inspect retained
`anvil-console.log` files after startup or routing failures.

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
- Keep `LICENSE`, wrapper, POM metadata, and CI/release workflows valid. Pull request verification
  is maintainer-requested through `workflow_dispatch` and runs the build/runtime and real-platform
  checks for the selected revision. Development publication is manual-only (`workflow_dispatch`).
  Release Drafter updates on `dev` pushes or manual dispatch,
  using `feature`, `change`, `bug`, `dependencies`, `major`, and `skip-changelog` labels. Published
  releases trigger release verification/publication. No scheduled nightly workflow is required.
