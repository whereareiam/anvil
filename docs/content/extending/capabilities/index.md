---
title: Overview
description: Create typed behavior for a player or process and supply its implementation.
---

Create a capability when test authors need another typed action or observation. Start by choosing
its owner, then choose the mechanism that supplies the behavior.

| Behavior | Public contract | Provider |
|---|---|---|
| Player behavior using observations or other capabilities | `PlayerCapability` | `PlayerCapabilityProvider` |
| Player actions through a protocol backend | `PlayerCapability` | `ProtocolPlayerCapabilityProvider` |
| Player observations or actions through agents | `PlayerCapability` | `AgentPlayerCapabilityProvider` |
| Server or proxy behavior through its agent, independent of a player | `ProcessCapability` | `AgentProcessCapabilityProvider` |

`ProcessCapability` belongs to the global API. A custom `RunningProcess` implementation can
supply it through the inherited `CapabilityOwner` lookup contract without an agent.
`AgentProcessCapabilityProvider` is the factory used when an agent supplies that process behavior.

For example, `PluginCommands` could belong to a player and use `Messages` to execute commands with
that player's permissions. A process capability could read your plugin's server-wide state
through a native handler. Using an agent to implement a feature does not by itself make the feature
process-owned: the built-in `Server` capability observes one player's identity and route. Its provider
uses shared player observations, so it needs neither a protocol channel nor direct agent requests.

## Create the example capability

This section builds a process-owned `Echo` capability backed by an agent. It proves that your
packaged native handler can run in the selected process and return a result, without creating a
simulated player. Replace its contract with a useful domain channelOperation after the installation
path works.

1. [Define the public contract](./contracts/index.md).
2. Define its [channelOperation contract and handler](../agent-operations/contracts/index.md), then build
   the [agent provider](./agent-adapters/index.md).
3. [Install the handler](../agent-operations/installation/index.md) and
   [package and test the extension](../packaging/index.md) through a consumer build.

A [protocol adapter](./protocol-adapters/index.md) supplies player behavior carried by packets.
That path does not require completing the agent example first.

## Arrange the artifacts

| Example artifact | Owns | Anvil API dependency |
|---|---|---|
| `echo-api` | `Echo` and its public models | `api` |
| `echo-operations` | Shared request/response descriptors | `agent-api` |
| `echo-host` | Process capability provider using a typed request channel | `capability-agent-api` |
| `echo-agent` | Handler loaded by the platform agent | `agent-server-api` |
| `echo` | Consumer dependency wiring | The API and selected host implementation |

Your artifacts use your own Maven group and package namespace. The host and agent artifacts share
channelOperation contracts through `echo-operations`, which exports `agent-api` for its descriptor
types. Host wiring derives a `ChannelOperation` from those descriptors and sends it through the
capability request channel. The host does not depend on the handler implementation. For packet behavior,
the protocol adapter occupies the host implementation role and may also supply worker-side code.
Anvil artifact IDs in this table use the Maven group `me.whereareiam.anvil`.

## Declare dependencies and support

The runtime orders creation by declared predecessor capabilities. Access to other capabilities is
restricted to those dependencies, within the same owner. A player capability can declare `Messages`
and obtain it through `context.requireCapability(Messages.class)`. A process capability backed by
an agent can declare `Console` and obtain it through its provider context.

All capability providers return a `CapabilityDescriptor` from `descriptor()`. It contains provider
identity, contract version, and required capability types. The shared `capability-api` owns this
contract, generic provider/context interfaces, typed request descriptors/channels, and neutral player
contracts under `capability.api.player`. Use `PlayerCapabilityProvider` when the implementation
needs only player identity/version, observations, declared dependencies, and cleanup.

Protocol-backed player providers and native worker bindings use `capability-protocol-api`.
`ProtocolPlayerCapabilityProvider` adds `supportedProtocolIds()`; its creation context supplies the
protocol channel and external backend services. An empty ID set allows any selected backend.
Declare `Session` only when the implementation uses it.

Agent-backed process and player providers use `capability-agent-api`, which includes the shared
capability contracts and neutral player context. Its process/player contexts add scoped request
channels for native agent work. `AgentProcessCapabilityProvider`
can restrict platforms with `supportsPlatform(...)`.
[Agent providers](./agent-adapters/index.md) explains both scopes and their cleanup hooks.

For exact signatures, consult the
[PlayerCapabilityProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-capability/capability-api/src/main/java/me/whereareiam/anvil/capability/api/player/PlayerCapabilityProvider.java),
[ProtocolPlayerCapabilityProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-capability/capability-protocol-api/src/main/java/me/whereareiam/anvil/capability/protocol/api/player/ProtocolPlayerCapabilityProvider.java)
and [AgentProcessCapabilityProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-capability/capability-agent-api/src/main/java/me/whereareiam/anvil/capability/agent/api/process/AgentProcessCapabilityProvider.java)
source Javadocs. These links use `dev`; select your release tag when checking a released version.

## Implement an existing capability

An alternative implementation of `Movement` supplies the existing API's methods. Arrange the
installed wiring and protocol selection so exactly one provider contributes `Movement.class` to a
player. The same uniqueness rule applies to capabilities contributed to a process. Multiple matching
providers are an error.

Capability lookup uses the exact registered class. A Java interface extending another capability
interface does not automatically register the parent type. There is no implicit augmentation of an
already installed capability. To compose additional behavior, define a capability with explicit
dependencies on the existing APIs.
