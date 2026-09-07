---
title: Paper
description: Run a pinned Paper distribution and supply server settings and plugin assets.
---

Use Paper for scenarios that exercise Bukkit or Paper plugins against a real server.
Complete [installation](../../../../../getting-started/installation/index.md), then apply
`me.whereareiam.anvil.platform.paper` at the same version as your other Anvil plugins.

## Declare the process

Place this fragment inside your scenario definition:

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;

MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.PAPER)
		.distribution(Distribution.remote("1.21.11", "132"))
		.setting("view-distance", "4")
		.setting("simulation-distance", "4")
		.build();
```

Register `server` on the scenario and select its name as the entrypoint for a direct test.
Install the plugin under test using its [registered workspace artifact](../../../workspaces/index.md).
The [first test](../../../../../getting-started/first-test/index.mdx) shows this complete workflow.

The provider resolves the selected version and build through PaperMC Fill and verifies the supplied
artifact checksum. Use [the compatibility table](../../versions/index.md) when choosing another pinned
server/client version. Local and named Paper executable artifacts are also supported with an explicit
`minecraftVersion`.

## Configuration and forwarding

`.setting(...)` writes Java property values to `server.properties`. For Paper-specific YAML settings,
prepare `config/paper-global.yml` or other configuration files as workspace assets. Anvil updates its
runtime fields structurally after copying those assets.

Paper supports modern forwarding from Velocity and legacy forwarding from Velocity or BungeeCord.
Anvil configures `config/paper-global.yml` and `spigot.yml` for the negotiated route; do not inject
forwarding secrets or listener ports into fixtures. See [forwarding](../../proxies/forwarding/index.md).

Paper's provider caches its `libraries` directory by default. This does not preserve plugin data or
world changes between disposable runs; declare those separately if your test requires them.

Run your test with `./gradlew anvilTest --tests 'your.package.YourTest'`. A ready process means Paper
and its agent started; use player and plugin assertions to establish the behavior under test.
