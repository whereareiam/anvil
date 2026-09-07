---
title: Velocity
description: Configure a pinned Velocity proxy and express typed TOML settings.
---

Apply `me.whereareiam.anvil.platform.velocity` alongside the backend platform unit.
Start with the complete [proxy environment](../index.md); it uses Velocity `3.5.1` build `615`.
The unit supplies the provider and its matching Velocity agent.

## Set the distribution

Use `Distribution.remote("3.5.1", "615")` with `Platforms.VELOCITY` on a `MinecraftProxy`.
The version names the Velocity release, while each backend independently declares its Minecraft version.
The provider resolves the selected build through PaperMC Fill and requires Java 21.

## Supply TOML values

A Velocity `.setting(key, value)` must contain a TOML literal. These are fragments to add to the
`MinecraftProxy.builder()` chain from the proxy guide:

```java
.setting("advanced.login-ratelimit", "0")
.setting("advanced.connection-timeout", "10000")
```

Dotted keys address nested TOML tables. Use numbers such as `"10000"`, booleans such as `"true"`,
arrays such as `"[1, 2]"`, and quoted TOML strings such as `"'hello'"`.
A plain Java string containing `hello` alone is not a TOML string literal.

The zero login rate limit is useful for rapid reconnect tests; configure it only when the journey
needs repeated connections. Run that journey after changing the setting and verify the reconnect
through Session and Server observations.

Anvil rejects settings for runtime-owned keys such as `bind`, `online-mode`, `servers`, and
`player-info-forwarding-mode`. Configure topology and authentication through the proxy declaration.
Existing unrelated TOML values are preserved; serialization may change comments and formatting.

## Forward player identity

Velocity supports modern forwarding to Paper and legacy forwarding to Paper or Spigot. Anvil
negotiates a compatible mode for the connected group, writes its forwarding secret, and updates backend
configuration. See [topology and forwarding](../forwarding/index.md).

The provider reuses its `libraries` cache by default. It shuts down Velocity using its native
shutdown command when the scenario closes.
