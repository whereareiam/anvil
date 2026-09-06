# AGENTS.md

## Purpose and scope

Anvil is a Java 21 testing framework for local Minecraft Java Edition processes and native-protocol
players. Preserve reproducibility, protocol fidelity, complete cleanup, and useful failure diagnostics.
Docker, Kubernetes, SSH/hosted orchestration, Fabric, Sponge, Bedrock, rendering, pathfinding,
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

| Area                                            | Responsibility                                                                                                |
|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `anvil-api`                                     | Global scenario, process, context, player, observation, and capability contracts; no Anvil project dependency |
| `anvil-engine`                                  | Platform-neutral validation, provisioning, process lifecycle, runtime context, player management, and cleanup |
| `anvil-launcher`                                | Scenario-engine factory, property decoding, and shaded assembly; providers remain explicit additions          |
| `anvil-capability/capability-api`               | Capability-provider contracts and dependency descriptors                                                      |
| `anvil-capability/capability-runtime`           | Discovery, ordering, capability composition, and the protocol player-composer implementation                  |
| `anvil-capability/capability-builtin/<feature>` | Separate feature API, packet/agent adapter, and consumer wiring modules                                       |
| `anvil-protocol/protocol-api`                   | Backend-neutral providers, players, composition, and optional authentication contracts                        |
| `anvil-protocol/protocol-adapter-api`           | Host/worker execution contracts for packet-backed capabilities                                                |
| `anvil-protocol/protocol-mcprotocol`            | MCProtocolLib backend, pinned catalog, workers, and private authentication store                              |
| `anvil-platform/platform-api`                   | Provisioning SPI, artifact resolution, distribution validation, and forwarding contracts                      |
| `anvil-platform/platform-*`                     | Provider-specific distribution/configuration implementations and platform-agent assemblies                    |
| `anvil-agent/agent-api`                         | Typed agent operations, native service access, transport, directory, and artifact contracts                   |
| `anvil-agent/agent-common`                      | Authenticated loopback transport and external-operation loading                                               |
| `anvil-agent/agent`                             | Common shaded runtime packaged by platform agents                                                             |
| `anvil-integration/junit/extension`             | JUnit annotations, context injection, and lifecycle integration                                               |
| `anvil-integration/junit/gradle`                | Optional `anvilTest` task and JUnit dependency wiring                                                         |
| `anvil-tooling/tooling-runner`                  | Foreground scenario shell, independent of Gradle APIs                                                         |
| `anvil-tooling/gradle/scenarios`                | Gradle DSL, `src/anvil`, artifacts, scenarios, authentication tasks, and unit-plugin registry                 |
| `anvil-tooling/gradle/bundle`                   | Combined Gradle plugin and curated capability/platform unit plugins                                           |
| `anvil-testing`                                 | Grouping module for cross-module tests and reusable test artifacts                                            |
| `examples/proof-of-patience`                    | Consumer example, not a home for framework system assertions                                                  |
| `build-logic`                                   | Shared Java, testing, assembly, and publication conventions                                                   |
| `docs/content`                                  | Task-oriented Scriptorium guides for using, extending, and contributing to Anvil                              |
| `scriptorium.project.json`                      | Scriptorium project metadata and version policy                                                               |

## Dependency and extension rules

- Keep concrete platform SDKs, MCProtocolLib, transport implementations, and assembly modules out of
  `anvil-api`. Capability API modules depend only on `anvil-api` unless an intentional same-family
  public API relationship is approved.
- Implementation/adapters consume APIs and external libraries, not sibling implementations.
  Runtime composition and packaging belong at the outer assembly/integration boundary.
- Use `ServiceLoader` for the existing provider SPIs. Preserve service descriptors and merge them
  when shading. Do not add broad reflection scanning as an alternative discovery path.
- The engine selects one protocol provider before capability discovery. Automatic selection uses
  the sole installed provider; ambiguity requires an explicit ID.
- Public capabilities extend `PlayerCapability`. Backend-specific providers implement those APIs
  and declare their supported protocol IDs and actual predecessor capabilities.
- Retrieve only declared capability dependencies. The agent-backed `Server` capability must remain
  independent of `Session`.
- Packet behavior belongs in capability-owned adapters. Register namespaced worker operations and
  packet listeners rather than adding feature branches to the MCProtocol worker.
- External backends expose their own stable execution-service contracts through `ProtocolPlayer`.
  Do not disguise one library's packets as a universal cross-library packet abstraction.
- Authentication is an optional `ProtocolProvider` service. Tooling depends on that API, not
  MCProtocol's account-store implementation. The Gradle account option is `--auth-profile`.
- External agent handlers implement `AgentOperationProvider` and own their operation namespace.
  Install their JARs under `plugins/anvil-agent-extensions`; do not bundle Anvil agent APIs or
  platform SDKs into those JARs. Host capabilities borrow connections through `AgentDirectory`.
- Platform agents expose native services and their scheduling rules. The endpoint owns its extension
  class loader; the scenario owns host connections.
- `api.process.RunningProcess` exposes lifecycle, address, and console; server/proxy specializations live in `api.process.type`. Player identities/routes belong to
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
  agent credentials, and EULA values take precedence.
- Agents bind to loopback and authenticate with per-run random tokens. Non-loopback game listeners
  require a manual scenario and explicit LAN opt-in.
- Start servers before proxies; stop processes in reverse order. Cleanup attempts every resource,
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
- `anvil-testing/testing-runtime/src/test`: cross-module discovery/composition without live Minecraft.
- `anvil-testing/testing-server/src/test`: real sessions, capabilities, routes, and external agents,
  grouped by behavior. The whole task requires `-Panvil.testMode=full`.
- `testing-fixtures/fixtures-server-plugin` and `fixtures-external-extension` use `src/main` and
  produce real test JARs. Share fixtures only when reused or when a packaging boundary requires them.
- Do not mix live and non-live methods behind tags in one test class. Do not move framework assertions
  into the consumer example. Never introduce real online-account tests into CI.

Run the nearest relevant test first, then architecture/build checks:

~~~shell
./gradlew verifyArchitecture
./gradlew :anvil-engine:test
./gradlew :anvil-agent:agent-common:test
./gradlew :anvil-capability:capability-runtime:test
./gradlew :anvil-protocol:protocol-mcprotocol:test
./gradlew :anvil-tooling:gradle:scenarios:test :anvil-tooling:gradle:bundle:test
./gradlew :anvil-testing:testing-runtime:test
./gradlew build
~~~

For protocol behavior, run exact worker contracts for both catalog versions and full live coverage.
For kicking/reconnects/identities, include `PlayerIdentityReconnectSystemTest` for both versions.
For providers/forwarding, run every affected direct/proxy combination. Inspect retained
`anvil-console.log` files after startup or routing failures.

~~~shell
./gradlew test -Panvil.testMode=full
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
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
- Keep `LICENSE`, wrapper, POM metadata, and CI/release workflows valid. Ordinary PR verification
  runs build/runtime checks; approved live jobs run real-platform tests. Development publication is
  manual-only (`workflow_dispatch`). Release Drafter updates on `dev` pushes or manual dispatch,
  using `feature`, `change`, `bug`, `dependencies`, `major`, and `skip-changelog` labels. Published
  releases trigger release verification/publication. No scheduled nightly workflow is required.
