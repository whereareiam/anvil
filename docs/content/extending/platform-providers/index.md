---
title: Overview
description: Add provisioning and configuration for a server or proxy distribution.
---

A platform provider turns a scenario's server or proxy declaration into a verified executable,
platform configuration, and launch requirements. Both roles use the same `PlatformProvider` SPI.
Execution providers remain responsible for starting the process and supplying network endpoints.

Create a library depending on `me.whereareiam.anvil:platform-api` at the consumer's Anvil version.
Implement `me.whereareiam.anvil.platform.api.PlatformProvider` and register its class in
`src/main/resources/META-INF/services/me.whereareiam.anvil.platform.api.PlatformProvider`.

## Implement the platform contract

| Method | What to supply |
|---|---|
| `id()` | Stable value used by the scenario's `platform` field |
| `configurationType()` | Supported declaration type, such as `MinecraftServer` or `MinecraftProxy` |
| `validateDistribution(process)` | Selector validation without downloads or process execution |
| `resolve(process, context)` | Verified executable JAR and a useful description |
| `configure(process, context)` | Platform configuration using the engine's runtime values |
| `readinessPattern()` | Log expression that marks the process ready for players |
| `minimumJavaVersion(process)` | Minimum Java feature version for this distribution |

Override `programArguments`, `stopCommand`, and `defaultCaches` where the defaults do not fit your
platform. Add `forwardingModes` and `platformAgent` when your platform supports those features.

The
[PlatformProvider](https://github.com/whereareiam/anvil/blob/dev/anvil-platform/platform-api/src/main/java/me/whereareiam/anvil/platform/api/PlatformProvider.java)
source Javadocs define each method's inputs and behavior;
[PlatformContext](https://github.com/whereareiam/anvil/blob/dev/anvil-platform/platform-api/src/main/java/me/whereareiam/anvil/platform/api/model/PlatformContext.java)
documents the supplied runtime values. These links use `dev`; select your release tag when checking
a released version.

The provider runs in Anvil's host JVM. A [platform agent](./agents/index.md) runs in the managed JVM;
keep platform SDK dependencies in that artifact. Do not start processes or allocate independent
runtime ports inside `configure`.

## Follow the provisioning sequence

The engine validates selectors and topology, prepares artifacts and declared assets, then calls
provider configuration in declaration order. Configure the files from the supplied workspace rather
than assuming no files exist. Runtime-owned settings take precedence over prepared assets.

[Distributions and configuration](./distributions/index.md) covers this boundary in detail.
[Agents and forwarding](./agents/index.md) covers native services and proxy/server compatibility.

## Install and verify

Put the provider artifact on the consumer's `anvilPlatforms` configuration, then select its ID in the
server or proxy declaration. Ensure any required agent assembly is also available on the scenario
runtime classpath. Use an exact build or verified checksum in automated scenarios.

Verify discovery, selector failures, configuration preservation, readiness, graceful shutdown, and
Java requirements. For a proxy, run every supported direct/backend combination and inspect identity
forwarding through server observations. A successful process launch does not prove a forwarding
configuration is correct.
