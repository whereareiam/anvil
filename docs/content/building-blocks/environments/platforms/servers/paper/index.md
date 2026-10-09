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
prepare `config/paper-global.yml` (or `paper.yml` before 1.19) or other configuration files as
workspace assets. Anvil updates its runtime fields structurally after copying those assets.

Paper supports modern forwarding from Velocity and legacy forwarding from Velocity or BungeeCord.
Anvil configures `spigot.yml` and the file Paper reads for the server version: `paper.yml`
`settings.velocity-support` before 1.19, and `config/paper-global.yml` `proxies.velocity` from 1.19.
It does not create the other layout's file. Do not inject forwarding secrets or listener ports into
fixtures. See [forwarding](../../proxies/forwarding/index.md).

Paper's provider caches Paperclip's `cache` directory, which holds the downloaded Mojang server, and
from 1.18 also its `libraries` directory. This does not preserve plugin data or world changes between
disposable runs; declare those separately if your test requires them.

## Java

Paper runs on the LTS release its version prefers; see
[Java per platform version](../../../provisioning/java/index.md#java-per-platform-version) for the
defaults and ranges. Paper `1.16.5` and `1.17.x` refuse newer Java unless `Paper.IgnoreJavaVersion`
is set; an explicit newer request, such as `JavaRequirement.builder().featureVersion(17)` on
`1.16.5`, makes Anvil add `-DPaper.IgnoreJavaVersion=true`. Combinations above the
maximum are `UNTESTED`. See
[running above a maximum](../../../provisioning/java/index.md#run-above-a-platforms-maximum).

Run your test with `./gradlew anvilTest --tests 'your.package.YourTest'`. A ready process means Paper
and its agent started; use player and plugin assertions to establish the behavior under test.
