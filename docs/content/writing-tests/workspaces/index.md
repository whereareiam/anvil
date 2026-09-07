---
title: Workspaces and artifacts
description: Install packaged plugins and assets, reuse caches, and control workspace lifecycle.
---

# Workspaces and artifacts

Each process receives its own working directory. A `WorkspacePlan` declares assets, caches, and
cleanup separately; a cleanup rule does not implicitly install or cache anything.

## Register build artifacts

```kotlin
anvil {
    artifact("plugin-under-test", project(":plugin"))
    artifact("support-plugin", "com.example:support-plugin:1.2.3")
}
```

Artifact names are stable references. Gradle resolves their actual files and task dependencies.
Use `AssetSource.artifact(name)` for workspace assets or `Distribution.artifact(name)` for a server
executable. For a single-project build, use `tasks.named("jar")` as the artifact notation.

## Install an asset

```java
import java.nio.file.Path;

import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.type.AssetInstallMode;

WorkspaceAsset.builder()
        .group("plugin-under-test")
        .source(AssetSource.artifact("plugin-under-test"))
        .target(Path.of("plugins", "plugin-under-test.jar"))
        .mode(AssetInstallMode.ALWAYS)
        .build();
```

File sources copy to the target file. Directory sources copy their contents into the target
directory. Targets must remain below the process workspace; traversal, symbolic-link paths, and
overlapping targets are rejected by workspace validation.

`ALWAYS` refreshes the target each run. `SEED_ONCE` keeps an existing target, which is useful for
initial configuration in a persistent workspace. A fresh workspace starts from a generated empty
directory. `WorkspaceMode.PERSISTENT` retains the process directory and locks it against concurrent use.

Anvil installs assets and restores caches before provider configuration. Listener ports, EULA,
forwarding, and other runtime-owned settings take precedence over copied configuration.

## Cache generated files

```java
import java.nio.file.Path;

import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.type.CachePolicy;

WorkspaceCache.builder()
        .group("libraries")
        .path(Path.of("libraries"))
        .policy(CachePolicy.RESTORE_AND_SAVE)
        .build();
```

Providers can supply defaults, such as library directories. A scenario overrides a default by
declaring the same path, or disables it with `CachePolicy.DISABLED`. Cache policies also include
`RESTORE_ONLY` and `SAVE_ONLY`. Cleanup paths cannot overlap cache paths.

Successful completion saves enabled workspace caches. Failed preparation or runs do not save normal
success snapshots. Cache identity includes distribution and asset inputs to avoid reusing an
incompatible workspace snapshot.

## Cleanup and diagnostics

Cleanup rules select `BEFORE_START`, `AFTER_STOP`, or `ON_FAILURE`. See
[cleanup phases](cleanup.md) for the exact lifecycle semantics.

The default engine retains failed workspaces and writes `anvil-console.log` for each process;
successful disposable runs are removed. Keep failure diagnostics outside paths you explicitly
delete.

The default shared cache root is `~/.anvil`, and the default workspace root is `build/anvil`.
Configure them with `anvil.cacheDirectory` and `anvil.workDirectory`. The shared cache contains
distribution and protocol artifacts as well as provider-managed Java runtimes. The authentication
store is private state and must never be uploaded as a build cache or artifact.

See also [workspace cleanup](cleanup.md) for `WorkspaceCleanup`, `CleanupPhase`, and the common
cleanup patterns used by managed processes.
