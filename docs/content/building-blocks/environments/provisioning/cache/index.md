---
title: Overview
description: Choose a cache for downloaded software or reusable workspace files and configure its location.
---

Use caches to reuse inputs and generated files between runs. Choose the guide for the data you want
to retain:

| Guide                             | Use it for                                                                                                               |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| [Artifacts](./artifacts/index.md) | Downloaded server and proxy software, Java installations, and resolution metadata; includes offline and refresh settings |
| [Snapshots](./snapshots/index.md) | Selected files or directories produced in a process workspace, such as server libraries                                  |

## Configure the cache location

Both use the shared cache root, normally `~/.anvil`. Choose another location through
`anvil.cacheDirectory` in Gradle or `EngineOptions.cacheDirectory` when embedding. See
[Directories](../../configuration/directories/index.md) for the Gradle setup. Keep the same root when
preparing an environment and later running it offline.

## Share selected data

For another machine or CI run, copy only the cache data you intend to reuse. The root can also contain
private authentication profiles; exclude those account files rather than archiving the entire root.
See [Authentication](../../../players/authentication/index.md).
