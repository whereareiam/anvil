---
title: Runtime composition
description: Connect scoped services through global registration and typed assembly bindings.
---

`AnvilLauncher.builder()` defers assembly until `build()`. `LauncherAssembly` then acquires shared
services and creates the default scenario factory. The launcher supplies that factory directly to
`AnvilEngineBuilder`, transfers assembly ownership to the engine, and installs caller extensions.
An `EngineExtension` adds `ScenarioExtension` callbacks and transfers shared resources through
`EngineRegistration.own`. Registration closes before the engine accepts scenarios.

The factory is a required construction dependency, independent of optional extensions. A bare
`AnvilEngineBuilder(factory)` can build an engine without registering any extensions. The factory
is borrowed: a custom assembly either retains its resources or transfers them through `own`.
Failed launcher assembly releases acquired services; failed extension installation releases
transferred resources in reverse order, with assembly services released last.

The default scenario factory belongs to launcher assembly. `ScenarioFactory.create(scenario, observer)`
binds scoped services and returns a `ScenarioContext` before process startup. That context owns
prepared resources, starts its process group, and closes players before finalizing processes.
The engine retains it immediately and installs global attachments and setup once, after complete
startup reaches readiness.

`ScenarioEngine.prepare(...)` exposes that owned context for individual controls.
`ScenarioEngine.start(...)` prepares a context and completes its `start()` before returning.
JUnit, the foreground runner, and the [IDE session](../tooling/index.md) share these phases;
individual controls retain one wired environment across server/proxy starts and stops.

## Compose capabilities for each owner

Every installed `ProtocolLibraryProvider` can serve players. Player management selects a library and
an exact release for each player: its declared `protocolLibrary`, else the scenario's or the engine's,
else the installed library with the strongest support level for its Minecraft version. Launcher
composition keeps one `PlayerCapabilityRuntime` per selected library, created on demand, and filters
protocol-backed providers by their `supportedLibraries()`. Agent-backed process capability selection
uses each process's declared platform, independently of player creation.

Shared capability composition validates dependencies, rejects competing providers for one capability
type within an owner, and orders creation by declared predecessors. Before capabilities are
created, neutral, protocol-backed, and agent-backed player factories contribute to one validated
player dependency graph. Filtering protocol adapters does not skip duplicate or dependency checks
for the remaining providers. For a player whose native worker does not install a protocol-backed
provider's capability, composition skips that provider and every provider depending on it, and records
the worker's reason for `CapabilityUnavailableException`. The launcher binds scoped factories into that composition:

| Provider | Owner and context |
|---|---|
| `PlayerCapabilityProvider` | One player, with identity/version, observations, declared dependencies, and cleanup |
| `ProtocolPlayerCapabilityProvider` | One player, with the shared context plus its protocol channel and the services of the player's protocol library |
| `AgentPlayerCapabilityProvider` | One player, with observations, process-selected request channels, and declared player dependencies |
| `AgentProcessCapabilityProvider` | One logical process, with its platform, request channel, and declared process dependencies |

All capability providers use shared descriptor, dependency, and cleanup contracts from
`capability-api`. Neutral player providers work with shared observations; they do not select a
protocol library or receive its services. Protocol channels and native worker providers live in
`capability-protocol-api`, with player events, waits, and installed-capability discovery.
Agent-backed factories live in `capability-agent-api` and request a typed `RequestChannel`, which
launcher bindings connect to an agent client. Both player-specific mechanism contexts extend the
shared player context.

Embedded handlers consume `agent-server-api`; both agent role APIs share native operation
descriptors from `agent-api` without depending on each other. Feature wiring derives capability
channel descriptors from shared native operation metadata. Neither the capability nor agent API
families expose the other's services, and assembly performs their typed binding.

`RunningProcess` implements `CapabilityOwner<ProcessCapability>`, so consumers retrieve capabilities
directly from the process without creating a player. Global process contracts do not require agent
APIs. A custom process implementation can supply capabilities through the same lookup contract.

Launcher `ProcessComposition` selects agent-backed factories and supplies their request channels.
Capability-owned adapters feed `ProcessCapabilityRuntime`, which validates each process graph and
retains one logical `ProcessCapabilities` owner. Launcher assembly supplies that core `CapabilityOwner`
as an execution input. Managed process handles delegate capability lookup to the owner directly;
Launcher preparation initializes each owner from `PreparedLaunch.started()` after agent attachment.
`CapabilityProcessGroup` finalizes the logical owners before the processes; execution operations and
handles stay with managed execution.

Capabilities initialize after their process first reaches readiness and survive generation
replacement. Execution assigns the immutable `RunningProcess.executionId()` UUID, so the observer,
lookup, and tooling share one authoritative execution identity. The agent client reconnects to the
new generation, and requests during disconnection report unavailability. Closing the process
capability owner makes lookups fail and availability checks return `false`.

Agent directory entries identify registered process clients, including processes that have not
started. `AgentConnection.available()` reports whether a connection is attached and locally open;
it does not probe remote health or guarantee a later request succeeds. Requests made without an
open connection throw `AgentUnavailableException`. An empty `AgentClient.identity()` result means
that a connected platform does not observe the player. Player observation skips unavailable agents
and tolerates disconnection before a query begins, while communication and remote operation failures
remain visible to the caller.

`PlayerObservation` is a global player contract, so the built-in `Server` capability can observe
identity and route information through a neutral `PlayerCapabilityProvider`, without depending on
`Session` or the protocol capability API. External protocol libraries may expose their
own stable SDK service interfaces through the protocol player's explicit extension hook.

## Bind preparation to execution

Platform planning resolves Java requirement/source precedence, process roles, startup dependencies,
and game endpoint exposure in `ProcessPlan`. Launcher assembly maps those values to execution inputs.
`JavaSelection` groups requirement and source at engine, scenario, and process levels. Planning
fills omitted members independently, then `JavaRequirementPlanner` reads the provider's version
data (`PlatformProvider.versionData()`), selects one exact LTS feature version from the applicable
Java row raised to the agent's declared minimum Java, adds a maximum bypass property as a planned JVM
argument when needed, and applies the support policy. The resolved selection passes through
`ProcessPlan` and execution's `ProcessRequest`; Java provisioning and Docker image mapping resolve
exactly that feature version. Custom planners must supply a requirement with a feature version;
its source may remain unspecified. They also supply `proxy`, `publishGame`, and ordered `dependencies`.
`NetworkPolicy` carries declared bind address, LAN permission, exposure, and access constraints into
execution. Provider-translated process endpoints remain separate runtime values.

Managed execution receives an `ExecutionPlan` containing locations, readiness rules, resource
budgets, and dependencies. Its separate `ExecutionPreparation` boundary prepares files once the
complete topology has been allocated. The returned `PreparedProcess` lives across restarts;
`PreparedLaunch` supplies each generation's command and attachment lifecycle.

Launcher bindings apply workspace and platform policies through their scoped APIs, create fresh
agent credentials for each launch, and attach connections after readiness. Managed execution calls
those lifecycle boundaries and releases targets before prepared inputs are finalized. It has no
cache, Java-provisioning, workspace, platform-provider, or agent API dependency.

Other bridges follow the same direction: artifact storage uses cache coordination, Java acquisition
uses verified artifact resolution, and protocol runtime resolution requests exact pinned worker
artifacts. The consumer's API describes the required operation. Its provider and policy remain in
the owning family.

## Keep native worker behavior scoped

The MCProtocol library reads its releases from `mcprotocol-releases.toml`, resolves each selected
release's pinned runtime closure into `<cache>/protocol/mcprotocol/<release version>/` and starts one
isolated worker per release. A release with an unpinned artifact cannot be launched. Preparation prints a warning
for each server whose default players would use it, so a scenario whose players all choose another library still
runs. A player that selects such a release is refused when it is created. The worker
shell never links against MCProtocolLib: the release's client segment implements the `mcprotocol-api`
client port and owns login, client information, teleport and disconnect packets.
Its protocol-owned worker contract is bound to capability worker composition by the launcher.
Feature wiring registers `WorkerExtension` from `capability-protocol-api`. Its `PlayerBindingContext`
supplies SDK/session access for the existing native player and, through `adapter(Class)`, the
release-specific port implementation of the segment selected for the worker's release. Native implementations own the actual
packets, state, and listeners; feature APIs keep domain models free of transport types.

`OperationRegistry` is the shared typed-handler boundary. `WorkerCapabilities` enforces binding-time
registration, unique namespaced operation IDs, and cleanup, then delegates the typed handlers.
Launcher `ProtocolOperationRegistry` registers the capability codec's encoded handlers with the
protocol's `NativeOperations`. `JsonCapabilityCodec` owns request decoding, response type checks,
and response encoding; dependency composition and feature handlers operate on typed values.

Each binding owns one player's state. Native listeners attach before login and are replaced with
their connection generation. The binding survives reconnects until player destruction; old native
session handles retain their original identity. Callbacks check their captured generation before
updating observations, including callbacks already in flight during listener removal.

The worker class path is the release's runtime closure followed by the host class path. Of the host
entries that carry segment properties, only the selected segment of each owner stays: the one with the
greatest start version not above the release key. A kept host entry that carries netty, MCProtocolLib,
CloudburstMC or adventure classes is refused, because those come only from the closure. The worker
verifies each selected segment's linkage manifest against the loaded release, including the access,
static or instance, and class or interface requirements the build recorded; a client segment that does not
link fails the worker, and an adapter of any other segment that does not link is unavailable, so the
capability asking for it is reported unavailable with the first failing member. JSON envelopes, host request transport, and dispatch implementations stay
private. The library
sends online credentials to workers only through private stdin; task inputs and diagnostics never
contain them.

## Package at the outer boundary

The launcher packages default engine and service implementations, including the host-side
`agent-client`. Platform providers and protocol libraries remain explicit runtime additions. Platform-agent
assemblies package `agent-server` for their target platforms, and feature wiring bundles install
their selected capability implementations.

The `bundle` convention merges service files and shades the declared `embedded` configuration.
Public API artifacts keep one shared class identity. Keep plain and shaded filenames distinct and
publish the intended artifact once. Resolve installed agents from exact classpath artifacts.

External agent operations load through a process-owned extension class loader. The endpoint closes
that loader during shutdown and failed startup. Host capabilities borrow scenario-owned request channels;
they do not own the endpoint or its class loader.

Run discovery tests with the independently packaged fixture, then compile a separate consumer against
published artifacts. Worker or agent changes also require exercising their real process boundary.
See [Testing Anvil](../../testing/index.md).
