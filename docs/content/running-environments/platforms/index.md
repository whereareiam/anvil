---
title: Platforms
description: Select server and proxy provider families, then choose the platform page that matches your topology.
---

# Platforms

Platforms are explicit Gradle unit plugins. Apply one for every platform family used in your
scenarios, then read the provider page for the exact distribution and forwarding behavior you need.

The full plugin prefix is `me.whereareiam.anvil.`. Each unit supplies its provider and matching
platform-agent artifact. Agents are installed into managed workspaces automatically; consumers do
not allocate agent ports or provide session tokens.

## Choose a family

- [Servers](./servers/index.md): Paper and Spigot distributions, Java requirements, and server-side configuration.
- [Proxies](./proxies/index.md): Velocity and BungeeCord distributions, forwarding, and route selection.

Minecraft server versions and client versions remain separate from the proxy's own version. A local
or named server JAR must declare `minecraftVersion` so Anvil can select a compatible native client.
Providers declare their own minimum Java feature version and configuration format rules.
