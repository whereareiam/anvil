---
title: Overview
description: Create a player capability, compose existing capabilities, or implement an existing API for a backend.
---

Create a capability when test authors need another typed operation or observation. Every capability
instance belongs to one simulated player. Its provider can use that player's protocol services,
other declared capabilities, and scenario services such as platform agents.

For method signatures and provider lifecycle contracts, read the
[PlayerCapabilityProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-capability/capability-api/src/main/java/me/whereareiam/anvil/capability/api/PlayerCapabilityProvider.java) and
[PlayerCapabilityContext](https://github.com/whereareiam/anvil/blob/dev/anvil-capability/capability-api/src/main/java/me/whereareiam/anvil/capability/api/PlayerCapabilityContext.java)
source Javadocs. These links use `dev`; select your release tag when checking a released version.

## Choose how to add behavior

| Goal | Supported approach |
|---|---|
| Expose a new operation to tests | Define an interface extending `PlayerCapability` and supply its provider and implementation |
| Build on existing player behavior | Create a new capability with declared dependencies, then use those capabilities in its implementation |
| Support an existing API on another backend | Implement that API and register a provider for the supported protocol IDs |

For example, a custom `PluginCommands` capability can declare `Messages` as a required capability
and obtain it through `context.requireCapability(Messages.class)`. Its implementation can then
expose typed methods for your plugin's commands and expected replies.

An alternative implementation of `Movement` supplies the existing API's methods. Arrange the
installed wiring and protocol selection so exactly one provider contributes `Movement.class`.
Registering a second matching provider is an error, not an override or an extra layer.

Capability lookup uses the exact registered class. A Java interface that extends another capability
interface does not automatically register the parent type. There is no implicit augmentation of an
already installed capability.

## Create the example capability

This section builds an `Echo` capability whose host adapter calls a platform agent. Echo is a small
transport example: it proves that your packaged handler can run in the selected process. Replace
its contract with a useful domain operation after the installation path works.

1. [Define the public contract](./contracts/index.md).
2. For the Echo example, define its [operation contract and handler](../agent-operations/contracts/index.md),
   then build the [agent adapter](./agent-adapters/index.md).
3. [Install the handler](../agent-operations/installation/index.md) and
   [package and test the extension](../packaging/index.md) through a consumer build.

A [protocol adapter](./protocol-adapters/index.md) is the alternative path for behavior carried by
player packets. It does not require completing the agent example first.

## Arrange the artifacts

| Example artifact | Owns | Anvil API dependency |
|---|---|---|
| `echo-api` | `Echo` and its public models | `anvil-api` |
| `echo-operations` | Shared request/response descriptors | `agent-api` |
| `echo-host` | Capability provider calling the agent | `capability-api`, `agent-api` |
| `echo-agent` | Handler loaded by the platform agent | `agent-api` |
| `echo` | Consumer dependency wiring | The API and selected host implementation |

Your artifacts use your own Maven group and package namespace. The host and agent artifacts share
operation contracts; the host does not depend on the handler implementation. For packet behavior,
the protocol adapter occupies the host implementation role and may also supply worker-side code.

## Declare what an implementation needs

`CapabilityDescriptor` identifies the provider, declares its supported protocol IDs, and lists
predecessor capability types. The runtime creates providers in dependency order. Access to other
capabilities is restricted to those declared dependencies.

An empty `supportedProtocolIds` set means the provider is independent of a particular backend.
Use it for agent-only behavior when the adapter has no backend-specific service dependency. Declare
`Session` only when the implementation uses that capability. The built-in `Server` observation
capability, for example, works independently of `Session`.

At most one selected provider can contribute a given capability type. If you provide an alternative
implementation of a built-in API, select compatible protocol IDs and avoid installing two matching
implementations for the same engine.
