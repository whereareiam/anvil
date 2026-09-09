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
| Player capability using shared observations/dependencies | `me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider` |
| Protocol-backed player capability provider | `me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider` |
| Agent-backed player capability provider | `me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider` |
| Agent-backed process capability provider | `me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider` |
| Native worker extension | `me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension` |
| Protocol provider | `me.whereareiam.anvil.protocol.api.provider.ProtocolProvider` |
| Platform provider | `me.whereareiam.anvil.platform.api.PlatformProvider` |
| Agent channelOperation provider | `me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider` |

Merge service files when shading several implementations into one artifact. Inspect the resulting
JAR, not only the source resource. Keep public API identities shared: agent extension JARs must use
the parent-provided Anvil APIs and platform SDK rather than bundling another copy.

## Update an existing extension

Use each scoped API at the same Anvil version as the host:

| Extension code | API artifact |
|---|---|
| Shared providers/contexts, neutral player contracts, typed requests, and handler registration | `capability-api` |
| Protocol-backed player providers, channels, and native worker bindings | `capability-protocol-api` |
| Agent-backed process and player capability providers | `capability-agent-api` |
| Host agent clients, connections, and directories | `agent-client-api` |
| Embedded native handlers and platform agents | `agent-server-api` |
| Shared agent request/response descriptors and payloads | `agent-api` |
| Protocol providers | `protocol-api` |
| Global engine extensions and public scenario, player, process, and capability contracts | `api` |

Both mechanism APIs, `capability-protocol-api` and `capability-agent-api`, include `capability-api`.
Neither includes the other or an agent transport API.
Both agent role APIs include the shared `agent-api` contracts. Neither agent role API includes the other.
Use `compileOnly` for parent-provided Anvil APIs and platform SDKs in installed agent extension JARs.

The `protocol-adapter-api` compatibility coordinate supplies a POM-only dependency bundle for
`protocol-api` and `capability-protocol-api`. It contains no classes. Prefer the direct scoped coordinates
for new integrations; the compatibility bundle does not restore old interfaces or adapt compiled
extension binaries. Anvil's BOM includes this coordinate alongside the canonical scoped artifacts.

Update imports, service filenames, and typed channelOperation descriptors together, then recompile and run
the packaged checks below. Artifact-coordinate compatibility does not make an extension compiled
against different SPI signatures binary-compatible.

## Verify three boundaries

1. **Contract behavior:** verify dependency declarations, supported protocol IDs, input handling, and
   meaningful observations using focused tests in the owning artifact.
2. **Packaged discovery:** load the produced JAR through the same service/class-loader boundary as a
   consumer. Test missing services and ambiguous providers with useful diagnostics.
3. **Runtime behavior:** run the consumer journey against the supported platform and protocol
   combinations. Check cleanup after startup failures, channelOperation errors, and player destruction.

For packet adapters, exercise exact worker contracts at each supported catalog version. For agent
handlers, test dispatch inside a real platform agent. For forwarding, assert the server-observed
player identity after direct and proxy connections.

Anvil's `test-extension` artifact and its runtime/live tests provide a repository example
of the packaging boundary. Its synthetic backend is suitable for discovery assertions; it does not
establish Minecraft compatibility. Repository commands are in [Testing Anvil](../../contributing/testing/index.md).
