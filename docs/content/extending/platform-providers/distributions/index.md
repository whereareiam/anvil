---
title: Distributions and configuration
description: Resolve immutable executables and write configuration that preserves user assets.
---

Implement distribution resolution and configuration as focused collaborators within your provider
artifact. The provider's public entry point delegates to them. This makes selector and file-format
behavior testable without launching Minecraft.

## Validate before provisioning

`validateDistribution(MinecraftProcess)` must perform no downloads or process execution. Validate
provider-specific selectors here so an invalid declaration fails before startup. The default
implementation accepts local and named artifacts and requires a build identifier for remote ones.
A checksum-pinned provider must validate its checksum rules explicitly.

Automated scenarios need an immutable build or content pin. For example, Spigot's provider uses
GetBukkit JARs selected with `Distribution.pinned(version, sha256)`. Do not silently replace a pin or
turn a missing cached artifact into an unpinned download. `latest` is limited to manual scenarios.
Local and named server artifacts also need their declared Minecraft version for native-client
validation.

## Resolve through the supplied context

`PlatformContext` provides an `ArtifactResolver`, cache root, process work directory, and scenario
values. Obtain remote content through its artifact resolver so offline mode, integrity checks, and
cache coordination follow the same contract as the rest of Anvil.

Return `ResolvedDistribution` with the executable JAR and a description that identifies the selected
distribution. Use exact artifact paths; do not guess another module's `build/libs` output. Write
provider-generated files only within the supplied work directory.

## Preserve unrelated configuration

The context supplies the allocated bind address and port, translated process addresses, EULA state,
and negotiated forwarding configuration. These values own the runtime settings in generated files.
Parse the platform's format, update the relevant structure, and preserve unrelated user values.

Test a prepared configuration containing custom settings. After `configure`, verify both that the
custom settings survive and that stale runtime ports, forwarding secrets, or EULA values have been
replaced. String append operations are unsuitable for formats where duplicate or nested keys can
change the result.

## Declare reusable caches

`defaultCaches(process)` returns cache paths that are safe to reuse for compatible distributions.
Scenarios can replace a default by declaring the same path or disable it with `CachePolicy.DISABLED`.
Keep generated application state out of a default cache unless reusing it has a clear compatibility
contract. See [Workspaces](../../../building-blocks/environments/workspaces/index.md) for the consumer controls.
