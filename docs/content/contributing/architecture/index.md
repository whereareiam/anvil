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

`anvil-engine` owns platform-neutral scenario orchestration: process lifecycle, workspace and
download provisioning, validation, shutdown, runtime context, and player management. It depends on
`anvil-api` plus the public provider SPIs in `anvil-platform/platform-api`,
`anvil-protocol/protocol-api`, and `anvil-agent/agent-api`. Providers, protocol backends, capability
implementations, and agent transports are discovered through those SPIs; the engine does not import
their implementation classes.

Protocol provider selection precedes capability discovery. The engine resolves one provider from
the runtime class loader and supplies its actual ID to the composer, even when no explicit ID is
configured. The selected backend is instantiated after scenario validation and closed by the engine.
Authentication tooling calls the provider's optional `ProtocolAuthentication` API independently of
backend creation. The Gradle adapter depends on `protocol-api`, not the MCProtocol implementation.

Within the engine, `ScenarioProcessLauncher` prepares and starts one process through its provider,
and `ProcessJavaResolver` selects its Java executable. `TemurinRuntimeProvisioner` owns the concrete
JDK download and extraction behavior. `ScenarioArtifactResolver` resolves named distributions and
workspace assets before provisioning and installs the declared platform agent. `WorkspacePlanValidator`
and `WorkspaceAssetFingerprint` separate declaration validation and cache identity from workspace
lifecycle. `ScenarioResources` owns the run's port assignments, players, agent connections, processes,
and workspaces from startup through shutdown. Both failed
startup and normal context shutdown use that owner; a failed cleanup does not skip later resources
or finalize diagnostic workspaces as a successful run.

`ForwardingPlanner` negotiates forwarding from provider capabilities before launch. Connected
proxy/server components share a compatible mode and per-run settings. Each platform module keeps
its distribution resolver and configuration writer beside its thin provider entry point; no provider
depends on another provider implementation. YAML and TOML serialization remain provider-owned.
Engine options live under `engine.model`; MCProtocol catalog services live under `mcprotocol.catalog`
and their shared values under `mcprotocol.model`.

`anvil-launcher` has no production source. Its shaded JAR packages the engine and selected reusable
runtime implementations for direct consumers, merges their service descriptors, and excludes public
API identities. It is a distribution boundary, not a second runtime layer.

The launcher's API and runtime variants both select its executable shaded JAR. The plain JAR has
its own classifier, so ordinary assembly and publication never overwrite the executable artifact.
Shaded artifacts are published through their component variants once. Gradle plugin publications
share the `pluginMaven` publication supplied by Gradle instead of competing with a second publication
at the same coordinates.

`anvil-tooling/tooling-runner` owns the reusable foreground scenario shell. It accepts an immutable
`AnvilRunnerConfiguration` and injected terminal streams, so it has no Gradle API dependency. The
Gradle plugin adapts task properties and the consumer runtime classpath into that configuration;
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

| Package | Responsibility |
|---|---|
| `api.provider` | Provider selection, backend lifecycle, and optional authentication |
| `api.player` | Backend-owned players and capability composition contracts |
| `api.model`, `api.type` | Reusable public values and closed types |
| `adapter.api.capability` | Worker adapter registration, named operations, and packet listeners |
| `adapter.api.player` | Host connection and worker-side player execution services |
| `mcprotocol.provider` | `McProtocolProvider` entry point and `McProtocolClientPool` ownership |
| `mcprotocol.authentication` | Account workflow and private credential storage |
| `mcprotocol.catalog`, `mcprotocol.model` | Pinned runtime resolution and shared internal values |
| `mcprotocol.model.worker`, `mcprotocol.type` | Typed private worker messages and core lifecycle identifiers |
| `mcprotocol.worker.host` | Child process lifecycle, remote player handles, requests, classpath, and diagnostics |
| `mcprotocol.worker.child` | Child composition root, lifecycle dispatch, MCProtocol players, and adapter installation |
| `mcprotocol.worker.transport` | Shared JSON-lines codec and serialized message output |

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
