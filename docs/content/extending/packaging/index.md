---
title: Packaging and testing
description: Deliver extension JARs with their service descriptors and verify the consumer boundary.
---

An extension is complete when a separate consumer can resolve its dependencies, discover its
providers, run its behavior, and clean up after failure. Test the packaged artifacts as well as
individual implementation classes.

## Put each artifact on the right path

| Artifact | Consumer installation |
|---|---|
| Capability API and host implementation | `anvilCapabilities` dependency, usually through a wiring artifact |
| Protocol backend | `anvilProtocols` dependency |
| Platform provider and required agent assembly | `anvilPlatforms` dependency or platform unit wiring |
| External agent handler | Workspace asset under `plugins/anvil-agent-extensions` |

See [Gradle integration](../../integrations/gradle/index.md) for configuration ownership. A host classpath
entry does not automatically install a JAR inside the managed server or proxy.

## Preserve service discovery

Put service files under `src/main/resources/META-INF/services/`. Each file contains the fully
qualified implementation class names, one per line.

| Extension | Service filename after `META-INF/services/` |
|---|---|
| Capability provider | `me.whereareiam.anvil.capability.api.PlayerCapabilityProvider` |
| MCProtocol worker adapter | `me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter` |
| Protocol provider | `me.whereareiam.anvil.protocol.api.provider.ProtocolProvider` |
| Platform provider | `me.whereareiam.anvil.platform.api.PlatformProvider` |
| Agent operation provider | `me.whereareiam.anvil.agent.api.operation.AgentOperationProvider` |

Merge service files when shading several implementations into one artifact. Inspect the resulting
JAR, not only the source resource. Keep public API identities shared: agent extension JARs must use
the parent-provided Anvil APIs and platform SDK rather than bundling another copy.

## Verify three boundaries

1. **Contract behavior:** verify dependency declarations, supported protocol IDs, input handling, and
   meaningful observations using focused tests in the owning artifact.
2. **Packaged discovery:** load the produced JAR through the same service/class-loader boundary as a
   consumer. Test missing services and ambiguous providers with useful diagnostics.
3. **Runtime behavior:** run the consumer journey against the supported platform and protocol
   combinations. Check cleanup after startup failures, operation errors, and player destruction.

For packet adapters, exercise exact worker contracts at each supported catalog version. For agent
handlers, test dispatch inside a real platform agent. For forwarding, assert the server-observed
player identity after direct and proxy connections.

Anvil's `fixtures-external-extension` artifact and its runtime/live tests provide a repository example
of the packaging boundary. Its synthetic backend is suitable for discovery assertions; it does not
establish Minecraft compatibility. Repository commands are in [Testing Anvil](../../contributing/testing/index.md).
