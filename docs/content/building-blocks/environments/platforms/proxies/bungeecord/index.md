---
title: BungeeCord
description: Configure a pinned BungeeCord proxy with YAML settings and legacy forwarding.
---

Apply `me.whereareiam.anvil.platform.bungeecord` alongside your backend platform unit.
The unit supplies the provider and matching BungeeCord agent. Its current Java minimum is 21.

## Select a Jenkins build

In the complete [proxy environment](../index.md), replace the proxy platform and distribution with
these builder fragments:

```java
.platform(Platforms.BUNGEECORD)
.distribution(Distribution.remote("BungeeCord", "2085"))
```

`BungeeCord` names the Jenkins job and `2085` is the pinned build used in Anvil's compatibility catalog.
Keep the existing backend declarations, default server, and scenario entrypoint.
Run the same consumer test to establish that the player reaches the named lobby through BungeeCord.

## Supply YAML settings

Proxy `.setting(key, value)` entries are parsed as YAML values in `config.yml`.
For a journey that deliberately reconnects immediately, add this builder fragment:

```java
.setting("connection_throttle", "-1")
```

Anvil rejects entries for runtime-owned keys, including `online_mode`, `ip_forward`, `listeners`,
`servers`, and `network_compression_threshold`. Use `.onlineMode(...)`, `.server(...)`, and
`.defaultServer(...)` to express the corresponding scenario behavior.

To seed other YAML values, install `config.yml` as a workspace asset. The writer preserves unrelated
values and replaces its managed routing fields. Malformed YAML or duplicate keys fail preparation;
comments and formatting may change during serialization.

## Route to Paper or Spigot

BungeeCord uses legacy forwarding. Anvil enables the matching backend configuration and waits for the
proxy and agent to become ready. All reachable backends must match the player's native version.
See [forwarding](../forwarding/index.md) for the supported combinations and route assertions.
