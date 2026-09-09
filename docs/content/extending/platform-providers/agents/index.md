---
title: Agents and forwarding
description: Expose native platform services and declare supported proxy-to-server forwarding modes.
---

Platform agents and forwarding are optional parts of `PlatformProvider`. Add the pieces your
platform supports and verify them through real server/proxy behavior.

## Supply a platform agent

Implement `PlatformAgent` in an artifact packaged as a plugin for the target platform. Its required
baseline reports `AgentInfo`, looks up observed identities, and executes console commands. Native
services are exposed through `findService`; `requireService` provides a diagnostic when unavailable.

Compile the platform plugin against `agent-server-api` and package the `agent` runtime artifact.
Server contracts use `me.whereareiam.anvil.agent.server.api`; shared identities and request payloads
come transitively from `agent-api`. During plugin startup, call
`AgentServerProvider.discover().start(platformAgent, logConsumer)` and close the returned
`AgentServer` during shutdown. Scenario assembly supplies per-generation authentication and execution-specific
endpoint settings. Use these supplied settings; do not create a separate unauthenticated endpoint.

The provider's `platformAgent()` returns a `PlatformAgentDescriptor` with the fully qualified
entry-point class name. The artifact locator resolves that class to the exact runtime JAR. The
default workspace target is `plugins/anvil-platform-agent.jar`; override it when your platform's
loading layout requires another path.

## Define scheduling behavior

Implement `PlatformAgent.call` according to your native API's threading rules. The bundled Bukkit
agent schedules work onto Bukkit's main thread and waits for the result. The default implementation
runs on the request thread, which is only suitable where the platform allows it.

Document which native services your agent exposes and any additional constraints channelOperation authors
must observe. External handlers can then use the public
[agent-channelOperation contract](../../agent-operations/contracts/index.md) without linking against your
agent implementation.

## Negotiate forwarding

`forwardingModes()` returns supported modes in preference order. Servers declare the modes they
accept; proxies declare the modes they send. Platform planning negotiates a mode shared by every member
of a connected group, including a server reached through multiple proxies.

Write the supplied `PlatformContext.forwarding` configuration into your platform's files. Consume
its mode, secret, and proxy online-mode setting. Do not infer forwarding from platform names or
choose another mode during configuration.

Expose only combinations you can verify. Test the negotiated configuration, successful player login,
server-observed identity, and reconnect behavior. Network isolation remains the execution provider's
responsibility; correct forwarding alone does not prevent direct backend access. Consumer topology
choices are described under [Forwarding](../../../building-blocks/environments/platforms/proxies/forwarding/index.md).
