---
title: Runtime composition
description: Connect scoped services through global registration and typed assembly bindings.
---

`AnvilLauncher.builder()` creates an `EngineBuilder` with the default scenario executor. An
`EngineExtension` contributes global lifecycle behavior through `EngineRegistration`: it can
register a `ScenarioExecutor`, add `ScenarioExtension` callbacks, and transfer shared resources with
`own`. Registration closes before the engine accepts scenarios.

The default executor belongs to launcher assembly. It binds scoped services and returns a ready
`ScenarioContext` using the engine-owned `RunningScenario` implementation. That context closes
players before finalizing processes. The engine then attaches global scenario extensions and runs the setup hook.
External integrations such as JUnit and Gradle use this public lifecycle.

## Compose capabilities for each owner

Launcher discovery selects one `ProtocolProvider` for players: the sole installed provider is
automatic; several require an explicit ID. Protocol-backed capability selection uses that protocol ID.
Agent-backed process capability selection uses each process's declared platform, independently of
player creation.

Shared capability composition validates dependencies, rejects competing providers for one capability
type within an owner, and orders creation by declared predecessors. Before capabilities are
created, neutral, protocol-backed, and agent-backed player factories contribute to one validated
player dependency graph. Filtering protocol adapters does not skip duplicate or dependency checks
for the remaining providers. The launcher binds scoped factories into that composition:

| Provider | Owner and context |
|---|---|
| `PlayerCapabilityProvider` | One player, with identity/version, observations, declared dependencies, and cleanup |
| `ProtocolPlayerCapabilityProvider` | One player, with the shared context plus its protocol channel and external backend services |
| `AgentPlayerCapabilityProvider` | One player, with observations, process-selected request channels, and declared player dependencies |
| `AgentProcessCapabilityProvider` | One logical process, with its platform, request channel, and declared process dependencies |

All capability providers use shared descriptor, dependency, and cleanup contracts from
`capability-api`. Neutral player providers work with shared observations; they do not select a
backend or receive its services. Protocol channels and native worker providers live in
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
Capability-owned adapters feed `ProcessCapabilityRuntime`, which validates each process graph,
creates `ProcessCapabilities`, and attaches them through `CapabilityProcessGroup`. Each handle
describes one generation; capability instances belong to the logical process and survive generation
replacement. The agent client reconnects to the new generation, and requests during disconnection
report unavailability. Closing the process capability owner makes lookups fail and availability
checks return `false`.

`PlayerObservation` is a global player contract, so the built-in `Server` capability can observe
identity and route information through a neutral `PlayerCapabilityProvider`, without depending on
`Session` or the protocol capability API. External backends may expose their
own stable SDK service interfaces through the protocol player's explicit extension hook.

## Bind preparation to execution

Platform planning resolves Java requirement/source precedence, process roles, startup dependencies,
and game endpoint exposure in `ProcessPlan`. Launcher assembly maps those values to execution inputs.
Custom planners constructing `ProcessPlan` directly must supply the effective `javaRequirement`,
optional `javaSource`, `proxy` and `publishGame` flags, and ordered `dependencies`.

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
artifacts. The consumer's API describes the required channelOperation. Its provider and policy remain in
the owning family.

## Keep native worker behavior scoped

The MCProtocol backend resolves exact runtimes from its pinned catalog and starts isolated workers.
Its protocol-owned worker contract is bound to capability worker composition by the launcher.
Feature wiring registers `WorkerExtension` from `capability-protocol-api`. Its `PlayerBindingContext`
supplies SDK/session access for the existing native player. Native implementations own the actual
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

Worker classpath resolution preserves extension dependencies while selecting the catalog's protocol
JAR. JSON envelopes, host request transport, and dispatch implementations stay private. The backend
sends online credentials to workers only through private stdin; task inputs and diagnostics never
contain them.

## Package at the outer boundary

The launcher packages default engine and service implementations, including the host-side
`agent-client`. Platform and protocol providers remain explicit runtime additions. Platform-agent
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
