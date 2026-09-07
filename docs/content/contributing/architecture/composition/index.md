---
title: Runtime composition
description: Understand service discovery, capability selection, workers, and runtime assemblies.
---

Runtime composition happens at the launcher and integration boundaries. Public APIs remain shared,
while runtime dependencies supply the implementations discovered through `ServiceLoader`.

## Select the protocol before capabilities

`EngineProviders` discovers execution and platform providers and uses `ProtocolProviderRegistry` to
select the protocol ID. One installed provider is automatic; several require an explicit ID.
`ProtocolPlayerComposer` discovery then selects composition for that backend.

The capability runtime filters providers by supported protocol IDs, rejects competing providers for
a capability type, and orders creation by declared predecessors. A provider can retrieve only its
declared capability dependencies. Protocol-player services and scenario services are resolved through
`PlayerCapabilityContext`.

The scenario supplies `AgentDirectory` and `PlayerObservation` independently of the packet library.
This is why the built-in `Server` capability can observe a player without requiring `Session`.

## Keep worker internals private

The MCProtocol backend resolves exact native runtimes from its pinned catalog and starts isolated
workers. Host-side remote player handles send requests over the private worker channel; child-side
players own MCProtocolLib sessions. Capability adapters register namespaced operations and packet
listeners through `protocol-adapter-api`.

Worker classpath resolution preserves extension dependencies while selecting the catalog's protocol
JAR. Core lifecycle operations remain reserved. The JSON message envelopes, host request transport,
and worker dispatch classes are private implementation details, not extension APIs.

Authentication owns private profile storage and token refresh in the backend. Online credentials are
sent to a worker only through private stdin and do not appear in task inputs or diagnostics.

## Package at the outer boundary

The launcher embeds engine, execution, provisioning, capability-runtime, and agent-connection
implementations. Platform and protocol providers remain explicit runtime additions. Platform-agent
assemblies package the common agent runtime for their target platforms.

The `bundle` convention merges service files and shades the declared `embedded` configuration.
Public API artifacts keep one shared class identity. Keep plain and shaded output filenames distinct
and publish the intended artifact once. Resolve installed agents from exact classpath artifacts,
never another module's guessed build output.

External agent operations load through a process-owned extension class loader. The endpoint closes
that loader during shutdown and failed startup. Host capabilities borrow scenario-owned connections;
they do not own the endpoint or its class loader.

## Validate composition changes

Run discovery tests with a separately packaged external extension, then compile the standalone
consumer against the published artifacts. If a change affects workers or platform agents, exercise
that real process boundary too. See [Testing Anvil](../../testing/index.md).
