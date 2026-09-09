---
title: Overview
description: Run typed extension work inside a managed server or proxy.
---

This section explains how to create and install your own handlers. For the available server/proxy
interaction paths and how host code calls an agent, start with
[Interacting through agents](../../building-blocks/environments/actions/agents/index.md).

Agent operations let your library execute code inside an existing Anvil platform agent. The host
sends a typed request; a handler in the server or proxy returns a typed response. Use this boundary
for native observations or actions that the player protocol does not provide.

```text
Test → running process → Echo capability → RequestChannel
                                           │ launcher binding
                                           ▼
                                       AgentClient
                                           │ request
                                           ▼
                              platform agent → Echo handler
```

The shared channelOperation contract belongs on both sides. Platform SDK code belongs only in the handler
artifact. The public capability API stays separate and depends only on `anvil-api`.
The process owns the capability; an agent channelOperation supplies its native handler mechanism.

For registration and native-service method contracts, read the
[AgentOperationProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-agent/agent-server/server-api/src/main/java/me/whereareiam/anvil/agent/server/api/operation/AgentOperationProvider.java),
[AgentOperationRegistry](https://github.com/whereareiam/anvil/blob/dev/anvil-agent/agent-server/server-api/src/main/java/me/whereareiam/anvil/agent/server/api/operation/AgentOperationRegistry.java), and
[PlatformAgent](https://github.com/whereareiam/anvil/blob/dev/anvil-agent/agent-server/server-api/src/main/java/me/whereareiam/anvil/agent/server/api/PlatformAgent.java)
source Javadocs. These links use `dev`; select your release tag when checking a released version.

1. [Define the channelOperation and handler](./contracts/index.md).
2. [Install the handler JAR](./installation/index.md) into the managed workspace.
3. Call it from an [agent-backed process capability](../capabilities/agent-adapters/index.md).

The Echo example demonstrates installation and request dispatch. For a useful test observation,
return application state from the native platform service and assert that state in the journey.
