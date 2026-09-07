---
title: Servers
description: Choose a server platform, pin a distribution, and keep the reachable client version compatible.
---

# Servers

Anvil treats servers as explicit platform providers. Each server family owns its distribution
resolution and configuration writer, while scenarios choose the server version and the native client
version that can reach it.

Use [Paper](paper/index.md) when you want a current upstream server distribution. Use [Spigot](spigot/index.md)
when you need the GetBukkit supplier and checksum-pinned content.

The current compatibility matrix covers server versions `1.21.11` and `26.1.2`. Keep the scenario's
`minecraftVersion` aligned with the reachable server when you register a local or named executable.

Server providers own their configuration format and Java requirement. Anvil preserves unrelated
settings when it rewrites the files the provider owns.
