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
| `configure(process, context)` | Platform configuration using the allocated runtime values |
| `readinessPattern()` | Log expression that marks the process ready for players |
| `versionData()` | Location of the provider's `<platform>-versions.toml` resource |

Override `jvmArguments`, `programArguments`, `stopCommand`, and `defaultCaches` where the defaults do not fit your
platform. Add `forwardingModes` and `platformAgent` when your platform supports those features.

## Declare Java and version data

Keep version data in a `<platform>-versions.toml` resource beside the provider class and return its
location from `versionData()`. The provider only exposes the resource; Anvil's platform planning reads
and validates it once per engine, before anything is downloaded.

```java
@Override
public @NotNull URL versionData() {
	return YourPlatformProvider.class.getResource("your-versions.toml");
}
```

```toml
# Versions your data supports; Anvil assesses them as COMPATIBLE.
known = ["1.20.4", "1.20.6", "1.21.11"]

# Oldest Java your platform agent runs on. Required when platformAgent() installs an agent.
[agent]
minimumJava = 17

# Version -> Java feature versions you have verified (VERIFIED).
[verified]
"1.21.11" = [21]

# Each row applies from `since` until the next row starts.
[[java]]
since = "1.20"
minimum = 17
maximum = 20
preferred = 17

[[java]]
since = "1.20.5"
minimum = 21
preferred = 21
```

`preferred` must be an LTS release inside `[minimum, maximum]`; planning uses it when no declaration
requests a version. `maximum` is optional and models the platform's own refusal of newer Java. Add
`maximumBypassProperty` only when the platform offers a system property that lifts that refusal;
planning then adds `-D<property>=true` for an explicitly requested newer LTS. Without it, planning
refuses such a request. Only the first row may omit `since`; it then applies from the oldest version,
which suits a platform whose builds carry no version. Unknown keys are refused.

`[agent] minimumJava` raises every row: planning never selects older Java for the platform, raises a
preferred version below it to the next LTS release, and refuses a version whose maximum leaves no LTS
release for the agent unless the declaration explicitly requests Java above the maximum through a
bypass. Verified Java must not be older than the agent's minimum.

`platformVersion(process)` keys the data: by default a server's native Minecraft version and no
version for proxies. Override it when your proxy's data is keyed by its own release; a process without
a version uses the newest row and is assessed by its Java range only.

`jvmArguments(process, consoleColors)` supplies platform launch defaults before the declaration's explicit
JVM arguments. Use `consoleColors` to request ANSI output through your platform's supported
console settings. The planner stores these defaults in `ProcessPlan`; the launcher combines them with
explicit JVM arguments when constructing the command. `PlatformPreparer` resolves software and applies
configuration. Streams remain pipes; enabling colors must not require an interactive terminal.
Keep these platform-specific options in the provider. The engine and IDE do not branch on platform IDs.

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

Platform planning validates selectors and topology. The assembled preparation path resolves
artifacts and declared assets. Each process start then calls provider configuration before launching
that process, including on its first start and every restart. Independent starts may configure
processes concurrently; modify only the supplied process workspace and use the planned forwarding
values and peer addresses. Configure existing files from that workspace. Runtime-owned settings
take precedence over prepared assets.

[Distributions and configuration](./distributions/index.md) covers this boundary in detail.
[Agents and forwarding](./agents/index.md) covers native services and proxy/server compatibility.

## Install and verify

Put the provider artifact on the consumer's `anvilRuntimeOnly` configuration, then select its ID in the
server or proxy declaration. Ensure any required agent assembly is also available on the scenario
runtime classpath. Use an exact build or verified checksum in automated scenarios.

Verify discovery, selector failures, configuration preservation, readiness, graceful shutdown, and
Java requirements. For a proxy, run every supported direct/backend combination and inspect identity
forwarding through server observations. A successful process launch does not prove a forwarding
configuration is correct.
