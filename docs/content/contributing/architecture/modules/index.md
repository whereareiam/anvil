---
title: Module boundaries
description: Locate the owning Gradle family and keep dependencies pointed toward public contracts.
---

Begin in the module that owns the behavior. Its API describes the boundary; implementations consume
that API; assembly and integration modules select and package the implementations.

| Family or module | Owns |
|---|---|
| `anvil-api` | Global scenario, context, process, player, observation, and capability contracts |
| `anvil-engine` | Validation, scenario orchestration, player management, and cleanup |
| `anvil-execution` | Process execution contracts plus local and Docker implementations |
| `anvil-provisioning` | Verified artifacts, shared caches, Java inspection and installation |
| `anvil-platform` | Distribution providers, platform configuration, and platform-agent assemblies |
| `anvil-protocol` | Backend contracts, adapter services, and the MCProtocol implementation |
| `anvil-capability` | Provider SPI, discovery/composition, and individual capability families |
| `anvil-agent` | Typed operations, native-service contracts, transport, and shared agent runtime |
| `anvil-launcher` | Engine factory, property decoding, and shaded runtime assembly |
| `anvil-integration/junit` | JUnit lifecycle/context injection and optional Gradle test wiring |
| `anvil-tooling` | Foreground runner, Gradle scenario DSL, registry, and bundled unit plugins |
| `anvil-testing` | Cross-module tests and real fixture JARs |
| `build-logic` | Java, testing, assembly, and publication conventions |

`examples/proof-of-patience` is a standalone consumer build. It consumes published artifacts and stays
outside root project discovery. Framework system assertions belong in `anvil-testing`.

## Preserve dependency direction

`anvil-api` has no Anvil project dependency. Capability API modules depend only on `anvil-api` unless
there is an intentional same-family public API relationship. Concrete platform SDKs, MCProtocolLib,
transport implementations, and assemblies stay out of those contracts.

Implementations depend on APIs and external libraries, not sibling implementations. Introduce a
shared API only for a real extension or module boundary. Ordinary collaborators such as a
configuration writer and a distribution resolver remain concrete within their owning implementation.

Assembly modules can package implementations. Service descriptors belong with the implementation
or assembly that supplies the service, and shading must merge them.

## Keep packages navigable

Use feature-oriented packages. Keep contracts at the feature root, reusable public values under
`model`, and enums or closed value types under `type`. Physical directories match package names.
Small implementation-local carriers can be inner records; public models use separate top-level files.

Use imports, JetBrains nullability annotations, and useful multiline Javadocs on public APIs. Prefer
immutable values and Lombok for routine construction. Java source uses tabs; dependencies and other
shared build behavior follow `build-logic` conventions and the grouped version catalog.

## Verify a boundary change

Update project dependencies, public imports, service filenames, descriptor contents, and published
artifact wiring together. Run the owning module's tests and `./gradlew verifyArchitecture`. For a
packaging change, also verify [runtime discovery](../../testing/unit-runtime/index.md) and a consumer
build against the produced artifacts.
