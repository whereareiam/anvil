---
title: Assets
description: Install exact build outputs and test configuration into server or proxy workspaces.
---

Assets place supplied files inside a process workspace. An `AssetSource` is a local path or a named
artifact already supplied by the integration or embedding application; it does not download an
arbitrary URL. The asset then chooses the destination and replacement behavior.

The example below registers a packaged plugin with Gradle, then installs that exact file through a
workspace plan. An embedding application supplies named files through `EngineOptions` instead.

## Register the packaged JAR

For a single-project plugin, add to `build.gradle.kts`:

```kotlin
anvil {
	artifact("plugin-under-test", tasks.named("jar"))
}
```

If your deployable plugin is produced by another task, register that task's single deployable JAR
instead. In a multi-project build, `artifact("plugin-under-test", project(":plugin"))` references the
plugin subproject. A published support plugin can use a Maven coordinate:

```kotlin
anvil {
	artifact("support-plugin", "com.example:support-plugin:1.2.3")
}
```

The support coordinate is a placeholder for an artifact you actually publish or consume. Each
registered artifact must resolve to exactly one file.

## Attach it to the server

In `PaperScenario.define()` from the [first test](../../../../getting-started/first-test/index.mdx), add
these imports:

```java
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import java.nio.file.Path;
```

Add this call to the `MinecraftServer` builder before `.build()`:

```java
.workspace(WorkspacePlan.builder()
		.asset(WorkspaceAsset.builder()
				.group("plugin-under-test")
				.source(AssetSource.artifact("plugin-under-test"))
				.target(Path.of("plugins", "plugin-under-test.jar"))
				.build())
		.build())
```

Run `./gradlew anvilTest`. The artifact's build task runs before the test, and Anvil copies its output
into this server's `plugins` directory. The JAR must already contain the descriptor and implementation
required by the selected platform. Anvil does not turn an arbitrary Java library into a plugin.
Confirm loading with your plugin's startup output or an application behavior assertion.

## Copy fixture files

Use `AssetSource.path(path)` for a local file or directory. A file source copies to the target file;
a directory source copies its contents into the target directory. For example, to seed a plugin
configuration stored at `src/anvil/fixtures/config.yml`, use that source path and target
`Path.of("plugins", "MyPlugin", "config.yml")` in another asset.

The default `AssetInstallMode.ALWAYS` replaces the target when workspace preparation runs. For a
directory source, replacement removes the existing target directory first, so files absent from the
source are not preserved. Choose `SEED_ONCE` when existing generated state should survive; see
[persistent workspaces](../persistence/index.md).

Targets must stay below the workspace. Absolute paths, traversal, symbolic-link paths, and overlapping
asset targets are rejected. Put separate assets at distinct targets.

## Respect generated runtime settings

Anvil restores caches and installs assets before platform configuration. Platform-owned runtime
settings such as ports, agent credentials, forwarding, and EULA acceptance take precedence over copied
configuration. Keep your application settings in the asset and let Anvil assign the runtime values.

For a server executable, use `Distribution.artifact(name)` instead of copying it as a plugin asset;
see [distribution selection](../../provisioning/platform/index.md).
