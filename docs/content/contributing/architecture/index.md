---
title: Architecture
description: Understand the engine, protocol workers, platform agents, and their module boundaries.
---

# Module Architecture

Anvil keeps public contracts, reusable runtime implementations, and composition roots in separate
Gradle modules. The dependency direction is one-way: implementation modules consume contracts, and
assembly modules are the only place that wires implementations together.

## Runtime graph

```text
anvil-api
  <- anvil-engine
       <- anvil-launcher (shaded assembly)
       <- anvil-integration/junit/extension
       <- anvil-tooling/tooling-runner (reusable foreground runner)
            <- anvil-tooling/gradle/scenarios
                 <- anvil-integration/junit/gradle
                 <- anvil-tooling/gradle/bundle (combined composition)
```

## Engine lifecycle and ownership

Consumers create a `ScenarioEngine` through `AnvilLauncher.create(options)`. The launcher assembles
the engine implementation and returns the API contract. The engine coordinates a scenario through
installed platform, protocol, and agent services.
Implementation modules consume the provider APIs; platform SDKs and protocol implementations stay
in their owning modules. Use the following boundaries when changing startup or cleanup:

| Owner             | Responsibility                                                                           | Lifetime                             |
|-------------------|------------------------------------------------------------------------------------------|--------------------------------------|
| `AnvilEngine`     | Retain successful runs and the shared protocol backend                                   | One engine instance                  |
| `EngineProviders` | Discover installed providers and select the protocol/player composer                     | Engine construction                  |
| `ScenarioSession`     | Compose one active scenario context from its owned resources                             | One scenario session                 |
| `SessionResources`     | Acquire, retain, and release execution, process, player, and workspace resources         | One scenario session                 |
| `ProcessSlot` | Retain a declaration, prepared workspace, executable, provider context, and agent connection | One declared process across restarts |
| `ManagedProcess`  | Supervise one execution generation's readiness, state, and termination                   | One process generation               |

### Startup sequence

1. `EngineProviders` selects the protocol before discovering its player composer. The backend is
   created lazily after scenario validation.
2. `ScenarioSession` resolves artifacts and calls `ScenarioPlanner` to validate the declaration and
   build the immutable process and forwarding plan. `ForwardingNegotiator` performs only topology
   and compatibility decisions; it has no I/O or
   credential generation.
3. The run selects distinct candidate listener ports and creates forwarding credentials per connected
   group. One Java resolver is reused, with one executable selection for each required feature version.
4. Each `ProcessSlot` takes ownership before preparing its workspace. It resolves the executable
   and command once, configures its platform, launches a `ManagedProcess`, and connects its agent.
   Servers start before proxies.
5. The session creates players and executes setup against its `ScenarioContext`. The engine retains
   the session only after setup succeeds.

A preparation or setup failure closes the partially acquired run. Setup assertions keep their
original type, and cleanup failures are suppressed. Failed runs do not save success caches. The
shared protocol backend remains owned by the engine.

### Restart and shutdown

`ProcessRegistry` owns the authoritative collection of `ProcessSlot` objects. Named
lookups read that collection directly; collection methods create immutable snapshots on request. Restart asks the selected object to close its old agent connection and
JVM, reapply platform configuration, and launch another generation. It reuses the prepared command,
workspace, listener address, Java executable, and forwarding configuration. Assets and distributions
are not resolved again. Agent credentials are fresh, while borrowed agent handles remain stable.

Restart and run shutdown share one synchronization boundary. Failed replacement marks the run
unsuccessful even when its exception is caught. Finalization closes players, then all agent
connections, then JVMs in reverse launch order. Only after process cleanup does it finalize
workspaces, so an earlier failure prevents success-cache saves. All cleanup operations are attempted.

`ManagedProcessConsole` owns the generation's command writes, captured output, checkpoint waits,
and log file. The lifecycle supervisor uses readiness and output-closure notifications without
owning the console buffer itself.

`PortSelection` probes currently free ports and prevents duplicate selections within a run. It does
not reserve sockets for child JVMs: another application may bind a candidate before the child starts.
The resulting startup failure follows normal rollback; the engine does not silently rewrite the
prepared topology or retry with different ports.

Tests exercise actual local JVM generations and fake agent transports to verify reuse, fresh agent
credentials, shared forwarding secrets, rollback, reverse shutdown, and suppression of cleanup errors.
The Minecraft restart matrix verifies the same public behavior through platform providers and native
player connections.

### Package responsibilities

Public contract packages are relative to `me.whereareiam.anvil.api`:

| Package                      | Contents                                                   |
|------------------------------|------------------------------------------------------------|
| `scenario`                   | Scenario engine, context/access, definitions, hooks, and registry |
| `process`                    | Base process handle, console, and scenario process access  |
| `process.type`               | Specialized `RunningServer` and `RunningProxy` contracts   |
| `player`                     | Player and capability contracts                            |
| `model`, `type`, `exception` | Declarative models, closed choices, and public failures    |

Engine packages are relative to `me.whereareiam.anvil.engine`:

| Package                                | Contents                                                                              |
|----------------------------------------|---------------------------------------------------------------------------------------|
| Engine root                            | Engine entry point, service discovery, and option defaults                            |
| `scenario.session`                     | Active context, resource ownership, and workspace layout                               |
| `scenario.planning`                   | Immutable planning and validation                                                      |
| `scenario.planning.validation`        | Scenario structure and provider declaration validation                                |
| `scenario.topology`                   | Process topology and forwarding negotiation                                            |
| `scenario.process`                     | Process slots, registry, agent connections, and startup scheduling                    |
| `process`                              | Execution-generation supervision, console capture, and endpoint selection             |
| `process.type`                         | Server/proxy specializations of `ManagedProcess`                                      |
| `execution-api`                        | Provider/session/process execution contracts                                          |
| `execution-local` / `execution-docker` | Host process and Docker Engine implementations                                        |
| `provisioning.workspace`               | Workspace lifecycle, validation, cache identity/storage, and confined file operations |
| `player`                               | Context-owned players and observations                                                |

`ScenarioArtifactResolver` resolves named artifacts and installs the declared platform agent.
`JavaRequirement` describes the process Java identity and `JavaSource` describes an explicit runtime
origin. `provisioning-java` inspects local installations, resolves its built-in Foojay packages, and
extracts user-supplied archives. `provisioning-api` owns artifact contracts, while
`provisioning-java/api` owns Java installation and provisioning contracts. `provisioning-cache` owns
verified artifact storage, metadata, and cross-process entry locks. `WorkspacePlanValidator` validates asset/cache/cleanup declarations before
`WorkspaceSession` executes them. `WorkspaceCacheStore` owns cache identity and persistence, including
asset fingerprints. A session acquires workspace ownership before cleanup is permitted, and releases
its persistent lock even when cleanup fails. Secondary failures remain suppressed on the primary
failure. Platform providers own their distribution and configuration
formats, including YAML/TOML parsing and native SDK interactions.

## Configuration and failures

`api.model.EngineOptions` is the immutable configuration passed to engines and foreground runners.
Omitted environment-dependent paths remain unspecified in API and are resolved when the engine is
created. Launcher's `config.EngineProperties` decodes JVM properties for JUnit and command-line tooling;
`EngineDefaults` resolves environment-dependent paths. Keep property names and parsing out of the
options model. The scenario defines process startup deadlines; engine options define shutdown policy.
See [Configure an environment](../../running-environments/configuration/index.md) for usage and defaults.

Public scenario failures derive from `api.exception.AnvilException`. Use the category corresponding
to the failed operation: validation, provisioning, process execution, or scenario setup. Preserve
provider-specific exceptions at their boundaries and preserve original causes when adding context.
Use standard Java exceptions for caller misuse, such as an unknown lookup or an operation on a closed
owner. [Troubleshooting](../../running-environments/troubleshooting/index.md) documents how callers
interpret those failures.

## Distribution and tooling

`anvil-launcher` exposes `AnvilLauncher` and property decoding. Its public factory returns the
`ScenarioEngine` API contract. Its shaded JAR packages the engine and selected reusable
runtime implementations for direct consumers, merges their service descriptors, and excludes public
API identities. It is a distribution boundary, not a second runtime layer.

The launcher's API and runtime variants both select its executable shaded JAR. The plain JAR has
its own classifier, so ordinary assembly and publication never overwrite the executable artifact.
Shaded artifacts are published through their component variants once. Gradle plugin publications
share the `pluginMaven` publication supplied by Gradle instead of competing with a second publication
at the same coordinates.

`anvil-tooling/tooling-runner` owns the reusable foreground scenario shell. It accepts an immutable
`EngineOptions` and injected terminal streams. It depends on API contracts and launcher assembly,
with no direct engine or Gradle API dependency.
`RunnerTerminal` formats and flushes user-facing output; `InteractiveSession` dispatches commands and
coordinates scenario lifecycle. The Gradle plugin supplies options and the consumer runtime classpath;
other tooling integrations can use the same runner artifact without depending on the Gradle plugin.

`anvil-tooling/gradle/scenarios` keeps the public Gradle DSL, base wiring, foreground scenarios,
and shared task infrastructure. Its implementation-only state, dependency buckets, platform
mapping, and Gradle constants live under `model`, `type`, and `internal` packages; it has no JUnit
integration knowledge. It publishes `me.whereareiam.anvil.scenarios` and is the only generic Gradle
feature module.

`anvil-integration/junit` is a grouping project. Its `extension` module contains the reusable JUnit
annotation, lifecycle, and context integration and publishes the stable `junit` artifact. Its
`gradle` module owns the `anvilTest` task and is the only adapter that knows the `junit` integration
coordinate. The adapter depends on the generic scenarios module for shared project setup, but does
not depend on the JUnit extension artifact in production; it adds that artifact to the consumer's
framework configuration.

`anvil-tooling/gradle/bundle` is the thin convenience plugin for `me.whereareiam.anvil`; it applies
the scenarios plugin and the JUnit Gradle adapter. The separate plugin IDs
`me.whereareiam.anvil.scenarios` and `me.whereareiam.anvil.junit` remain available when consumers
want only one workflow. The aggregate is the only generic tooling artifact that explicitly includes
JUnit.

This keeps a scenarios-only consumer free of JUnit dependencies while allowing a consumer to opt into
JUnit explicitly or through the combined plugin.

## Provider families

- `anvil-protocol/protocol-api` defines backend-neutral protocol contracts. `protocol-mcprotocol`
  provides the pinned MCProtocolLib backend and isolated worker runtime.
- `anvil-platform/platform-api` defines server and proxy providers. The Paper, Spigot, Velocity, and
  BungeeCord provider modules own distribution/configuration behavior.
  Provider-owned distribution validation runs during engine preflight. Build-addressed suppliers
  require their build selector; Spigot downloads prebuilt GetBukkit JARs selected by exact version
  and SHA-256. Its supplier cache is separate from prior locally built Spigot artifacts, and the
  runtime contains no BuildTools execution path.
- `anvil-agent/agent-api` defines host-side connections, platform-agent contracts, and artifact lookup.
  `anvil-api` owns the shared player observation contract. `agent-common` implements the authenticated JSON-lines transport;
  `agent` packages its common runtime for platform-agent distributions.
  Platform plugins depend on `AgentServerProvider` and `AgentServer` from the agent API. The
  JSON-lines runtime registers the server provider in its own service descriptor, and platform-agent
  assemblies package it. Host-side `AgentConnection` exchanges request and response models without
  exposing Jackson's tree types. The existing wire DTOs retain their Jackson serialization metadata.
  Agent artifacts are resolved from their exact runtime-classpath JAR; class directories do not
  trigger searches or guesses in neighboring Gradle output folders.
  `AgentOperationProvider` is the external operation SPI. Extension JARs are declared workspace
  assets under `plugins/anvil-agent-extensions` and share the platform agent's API identities through
  the parent class loader. Providers register namespaced typed handlers only during installation.
  The endpoint owns their class loader, while the scenario owns the host connections. Player
  capability providers access those connections through the `AgentDirectory` execution service.
- `anvil-capability/capability-api` defines capability-provider contracts. `capability-runtime`
  discovers and orders providers and implements the protocol player-composer SPI. Built-in capability
  families keep their API, protocol/agent adapter, and source-free wiring bundle separate.
  Each provider receives a context restricted to its declared capability dependencies. Construction
  failure releases all registered provider resources; destruction retains cleanup failures while
  continuing through the remaining resources and the player-removal callback.
  MCProtocol host providers and worker adapters include `McProtocol` in their class names. Their
  public capability APIs remain reusable by other backends. The server observation adapter and its
  wiring have no Session dependency.

## Protocol package ownership

Protocol contracts and runtime code are grouped by responsibility. Package names below are relative
to `me.whereareiam.anvil.protocol`:

| Package                                      | Responsibility                                                                           |
|----------------------------------------------|------------------------------------------------------------------------------------------|
| `api.provider`                               | Provider selection, backend lifecycle, and optional authentication                       |
| `api.player`                                 | Backend-owned players and capability composition contracts                               |
| `api.model`, `api.type`                      | Reusable public values and closed types                                                  |
| `adapter.api.capability`                     | Worker adapter registration, named operations, and packet listeners                      |
| `adapter.api.player`                         | Host connection and worker-side player execution services                                |
| `mcprotocol.provider`                        | `McProtocolProvider` entry point and `McProtocolClientPool` ownership                    |
| `mcprotocol.authentication`                  | Account workflow and private credential storage                                          |
| `mcprotocol.catalog`, `mcprotocol.model`     | Pinned runtime resolution and shared internal values                                     |
| `mcprotocol.model.worker`, `mcprotocol.type` | Typed private worker messages and core lifecycle identifiers                             |
| `mcprotocol.worker.host`                     | Child process lifecycle, remote player handles, requests, classpath, and diagnostics     |
| `mcprotocol.worker.child`                    | Child composition root, lifecycle dispatch, MCProtocol players, and adapter installation |
| `mcprotocol.worker.transport`                | Shared JSON-lines codec and serialized message output                                    |

`ProtocolWorkerProcess` owns one child process. `WorkerClasspathResolver`, `WorkerRpcClient`, and
`WorkerDiagnostics` each own a focused part of its host-side infrastructure. `RemoteProtocolPlayer`
is the host handle; `McProtocolPlayer` owns the actual MCProtocolLib session in the child.
`McProtocolWorkerMain` validates the exact native codec and composes `McProtocolWorker`, which owns
player lifecycle and delegates capability operations to `WorkerCapabilityRegistry`.
`WorkerMessageCodec` owns envelope serialization, parsing, and validation; `WorkerMessageWriter`
serializes child output. These are internal collaborators, not extension
contracts. Tests mirror the production packages, including worker host and child tests.

The classpath resolver identifies a runtime by its codec resource, not a filename pattern, and
preserves unrelated extension dependencies. The host resolves player identity before sending typed
creation options over private stdin. Core control operations and player events have named types;
external namespaced capability operations remain open. Their adapters cannot register reserved
`create`, `destroy`, or `shutdown` operations. Malformed private messages fail without retaining raw
payloads in parser diagnostics. Host/child messages are internal and evolve together in the same
backend artifact; extensions depend on the public adapter API, not these envelopes.

`MicrosoftAuthentication` owns login and token refresh. `AuthenticationProfileStore` owns only
private profile-document persistence and atomic replacement. Neither serializable request models
nor authentication value diagnostics include access tokens. Worker request failures permanently
close that request channel; shutdown uses a separate short timeout. Backend closure prevents new
players and attempts to close every owned worker even if an earlier cleanup fails.

## Dependency rules

- `*-api` modules depend only on `anvil-api` or an explicitly approved same-family API.
- Implementation and adapter modules depend on APIs and external libraries, never another
  implementation or an assembly module.
- Assembly modules may depend on implementations solely to package and wire them.
- ServiceLoader descriptors are part of the owning implementation or assembly artifact; API modules
  contain only the contracts needed by callers.

Run `./gradlew verifyArchitecture` to check the inferred production dependency graph.

## Test ownership

`anvil-testing` groups two test modules and two reusable fixture artifacts:

- `testing-runtime/src/test`: cross-module provider discovery and capability composition checks.
- `testing-server/src/test`: real platform sessions, capabilities, routing, and external-agent tests.
- `testing-fixtures/fixtures-server-plugin/src/main`: the plugin and operations installed in test servers.
- `testing-fixtures/fixtures-external-extension/src/main`: an external backend, capabilities, agent operations,
  and the shared JAR/class-loader fixture used by both test modules.

Module-owned unit and focused integration tests remain in their owning modules. Both cross-module
test modules use ordinary `src/test`; fixture modules use ordinary `src/main` and are not published.
Runtime tests run by default. Server tests require `-Panvil.testMode=full`, and
`-PanvilMatrixFilter` narrows the cases in `ProxyServerCompatibilitySystemTest`.
Fixtures needed by a single test stay with that test unless an actual JAR boundary is required.

## External library packaging

The launcher embeds Jackson and SLF4J; platform-agent distributions embed the common agent runtime
and Jackson. The platform provider artifacts resolve YAML or TOML modules as ordinary runtime
dependencies. Those serializers are not added to the agents or the global API. The MCProtocol
backend and capability adapters use separate protocol, authentication, Adventure, and Netty runtime
dependencies; the backend selects the exact catalog protocol JAR for each worker. Lombok is compile
time processing, and platform SDKs are compile-only dependencies supplied by the running platform.
