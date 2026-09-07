---
title: Cache
description: Reuse acquired artifacts and metadata, prepare offline runs, and distinguish download storage from workspace snapshots.
---

Anvil retains acquired software so repeated runs can reuse their inputs. The default shared cache
root is `~/.anvil`; configure another location through `anvil.cacheDirectory` in Gradle or
`EngineOptions.cacheDirectory` when embedding. Keep the same root when preparing and later running
an environment offline.

## What is retained

The acquisition cache stores downloaded artifact files and resolution metadata. A platform provider
can first read cached build metadata, then locate and verify the selected executable. Java provisioning
also retains downloaded archives and prepared installations.

When an expected SHA-256 is supplied, Anvil verifies cached or newly downloaded artifact bytes against
it. A missing or invalid cached artifact needs acquisition again; offline mode cannot repair it.
Resolution metadata matters too: possessing a JAR alone may not be enough to resolve its remote build
selector without network access.

These inputs can remain outside the process workspace. Cache reuse does not mean that every executable
is copied into the workspace or that your plugin's generated data is retained automatically.

## Prepare an offline test run

First run the intended environment with normal online settings and the same cache root, or supply
its required local artifacts and Java installations. Then add this fragment to the consumer
`build.gradle.kts` after applying the JUnit or umbrella plugin:

```kotlin
import org.gradle.api.tasks.testing.Test

tasks.named<Test>("anvilTest") {
	systemProperty("anvil.offline", "true")
	systemProperty("anvil.refresh", "false")
	systemProperty("anvil.java.download", "false")
}
```

Run the prepared test with `./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'`, replacing
the class name with your own. The settings apply to the **Anvil test JVM**. They do not configure the
managed server JVM or the foreground `anvilScenario` task. For other entry points, use the
[engine options reference](../../configuration/engine/index.md).

The example requires already available software. If an acquisition input or its metadata is missing,
Anvil reports that failure instead of downloading it. The Java flag additionally requires a suitable
local or prepared Java installation; it does not merely toggle distribution downloads.

## Choose the acquisition policy

- `anvil.offline=true` makes Anvil's acquisition services use retained artifacts and metadata.
- `anvil.refresh=true` asks resolution to refresh moving metadata or selections. It does not replace an
  explicit build number or checksum in your declaration.
- `anvil.java.download=false` disables provisioning missing Java installations. It does not disable
  platform downloads, and it is not required merely to reuse a cache.

Offline and refresh cannot both be enabled; Anvil rejects that combination when creating the artifact
store. Offline acquisition is not a firewall for plugins or game sessions. Gradle dependency resolution
is also separate: Anvil's flag does not stop Gradle from resolving build plugins or Maven dependencies.
A named artifact has already been supplied as a local file by Gradle or the embedding application.
`AssetSource` accepts a local path or such a registered artifact, not an arbitrary download URL.

## Distinguish workspace snapshots

[Workspace caches](../../workspaces/caches/index.md) copy selected generated paths between a process
workspace and snapshots under `<cacheDirectory>/workspaces`. They restore before asset installation
and save according to workspace finalization and the selected policy. They are separate from acquired
artifact files and metadata, even though both use the same cache root.

Changing the download cache location does not enable workspace snapshots or persistence. Declare
those behaviors in `WorkspacePlan`.

When sharing cached data between machines or CI runs, select the reproducible inputs you intend to
reuse. Exclude the provider's private authentication directory and account files; do not archive the
whole cache root. See [authentication](../../../players/authentication/index.md).
