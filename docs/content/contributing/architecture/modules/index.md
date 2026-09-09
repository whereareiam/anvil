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
| `anvil-protocol/src`                           | Player registration, version selection, authentication compatibility, and observations                              |
| Other `anvil-protocol` modules                  | Backend-provider contracts, the MCProtocol implementation, workers, and authentication storage                      |
| `anvil-capability/src`                          | Dependency validation, agent-provider adaptation, process/player facades, and capability cleanup                                |
| `anvil-capability/capability-api`                | Generic composition, neutral player contracts, descriptors, exceptions, and typed requests |
| `anvil-capability/capability-protocol-api`         | Protocol-backed player providers/contexts, channels/events, and native worker bindings |
| `anvil-capability/capability-agent-api`          | Agent-backed process/player providers and scoped request-channel contexts |
| `anvil-capability/capability-builtin/player`     | Player feature APIs, native implementations, and provider/worker wiring                                             |
| `anvil-capability/capability-builtin/agent`      | Process feature APIs and agent-backed host wiring, including Console                                                     |
| `anvil-agent/agent-api`                          | Shared operation descriptors, payloads, identities, and exceptions                                                   |
| `anvil-agent/agent-client/client-api`            | Host clients, connections, directories, and artifact lookup                               |
| `anvil-agent/agent-client`                       | Connections, stable process clients and sessions, directories, and observations in the Anvil JVM                    |
| `anvil-agent/agent-server/server-api`            | Native services, operation handlers, and embedded endpoint contracts                                                 |
| `anvil-agent/agent-server`                       | Embedded native operation dispatch, transport endpoint, and external-handler loading                                |
| `anvil-launcher`                                | Global engine builder, default scenario executor, scoped-service bindings, and shaded assembly                      |
| `anvil-integration/junit`                        | JUnit lifecycle/context injection and optional Gradle test wiring                                                   |
| `anvil-tooling`                                 | Foreground runner, Gradle scenario DSL, registry, and bundled unit plugins                                          |
| `anvil-testkit/tests`                           | Cross-module runtime and live assertions                                                                            |
| `anvil-testkit/fixtures`                        | Independent consumer build producing real fixture JARs                                                              |
| `anvil-testkit/support`                         | Shared host-side artifact access and extension classloader lifetime                                                 |
| `build-logic`                                   | Java, testing, assembly, and publication conventions                                                                |

`examples/proof-of-patience` is a standalone consumer build. It consumes published artifacts and stays
outside root project discovery. Framework system assertions belong in `anvil-testkit`.

## Locate a capability or agent extension

Capability ownership and transport are separate. `Capability` and `CapabilityOwner` are global
identity/lookup contracts. `PlayerCapability` and `ProcessCapability` constrain the types accepted
by players and running processes. Their APIs do not require an agent transport.

`capability-builtin/player` contains player features. `capability-builtin/agent` contains process
features with agent-backed wiring, including Console; that directory describes the implementation
source, not a separate public owner. Public feature packages and artifact IDs remain feature-specific.

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
