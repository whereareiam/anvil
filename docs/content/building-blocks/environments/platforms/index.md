---
title: Overview
description: Choose server and proxy platforms and keep their distributions and native clients compatible.
---

A platform supplies the distribution, configuration, and readiness behavior for a managed process.
Server and proxy platforms share the same scenario lifecycle and are selected explicitly in your build.

## Choose a platform role

| Role | Available platforms | Used for |
|---|---|---|
| [Servers](./servers/index.md) | Paper, Spigot | Running worlds and server plugins, directly or behind a proxy |
| [Proxies](./proxies/index.md) | Velocity, BungeeCord | Accepting player connections and routing them to declared servers |

A proxy scenario applies a unit plugin for the proxy and for every backend platform it uses.
The umbrella Anvil plugin does not select a platform for you.

## Build up from one server

Start with the [server guides](./servers/index.md), which include pinned examples. Add a
[proxy](./proxies/index.md) only when the behavior depends on routing or forwarded identity.
The proxy guide builds on a named backend server.

When adapting an example, use [Platform provisioning](../provisioning/platform/index.md) to choose a remote
build, checksum, or supplied artifact, and [Versions and compatibility](./versions/index.md) to check
native-client and Java requirements. Every reachable backend must be compatible with the player.

Once the topology is declared, use [Provisioning](../provisioning/index.md) to supply its software
and [Configuration](../configuration/index.md) to choose execution settings.

Platform unit plugins supply the provider and its matching agent artifact. Anvil installs the agents
into managed workspaces and allocates their authenticated endpoints automatically.
To implement another platform, use the [platform extension guide](../../../extending/platform-providers/index.md).
