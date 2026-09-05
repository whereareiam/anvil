# Anvil

The Minecraft test framework that got tired of watching you test everything by hand.

[Documentation](docs/content/index.md) · [Getting started](docs/content/getting-started/index.md) ·
[Extending Anvil](docs/content/extending/index.md) · [Contributing](docs/content/contributing/index.md)

Anvil gives JUnit control over:

- real Paper, Spigot, Velocity, and BungeeCord processes
- the packaged plugin your users will actually install
- lightweight clients speaking the real Minecraft protocol
- complete environments that can also stay open for a developer to join

No mocked server. No full game client. No small orchestra of terminals waiting for you to conduct.
The only fake part is Alice, and she is far more consistent than you are after the twentieth
reconnect.

## The test is simple. Repeating it is not.

Suppose your plugin should show a message when a player meets some condition.

Testing it once is easy:

1. Build the JAR.
2. Start the server.
3. Join the server.
4. Recreate the condition.
5. Look at chat.

Then something changes:

- a permission check
- a dependency
- the server platform
- the Minecraft version

Back to step one. The check did not become difficult; it simply acquired a permanent claim on a
developer's time.

**And that was one message.**

A larger plugin accumulates whole player journeys. An authentication plugin may need to verify:

- new and returning players
- accepted and rejected credentials
- kicks and repeated reconnects
- direct joins and proxy joins
- backend transfers and forwarded identities
- restarts and persisted state

Now multiply that by the supported server platforms and Minecraft versions, then perform the sacred
ritual before every release. Eventually “we tested it” means “we remembered most of it.” Inspiring.

### Make the journey executable

With Anvil, the test does the boring part:

1. Describe the real environment once.
2. Create however many players the journey needs.
3. Let them join, chat, run commands, click, move, reconnect, or cross a proxy.
4. Verify what the players and servers actually observe.

Run the same journey after every change and before every release. Point it at another Minecraft
version instead of reenacting it. If a developer genuinely needs to look around, start the same
scenario in the foreground and hand them the join address.

When something fails, Anvil keeps the process logs. This is slightly more useful than remembering
that the message definitely appeared yesterday.

Keep your unit tests. Anvil handles the ugly part where the packaged plugin must survive contact
with an actual Minecraft server.

## Replace yourself with Alice

Anvil requires Java 21 or newer. It selects another JDK for a Minecraft server when necessary,
because naturally the server and the test framework cannot always agree on one.

### Teach Gradle the trick

Add the repositories to `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://registry.whereareiam.me/maven/packages")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }
}
```

Choose the plugin that matches the workflow:

| Plugin ID                        | What it installs                                        |
|----------------------------------|---------------------------------------------------------|
| `me.whereareiam.anvil.junit`     | Automated `anvilTest` execution only                    |
| `me.whereareiam.anvil.scenarios` | `anvilScenario` for listing and foreground environments |
| `me.whereareiam.anvil`           | Both workflows and all built-in capabilities            |

Platform units are applied separately: `me.whereareiam.anvil.platform.paper`,
`me.whereareiam.anvil.platform.spigot`, `me.whereareiam.anvil.platform.velocity`, and
`me.whereareiam.anvil.platform.bungeecord`. Capability units such as
`me.whereareiam.anvil.capability.inventory` can be applied with the scenarios plugin when the
aggregate capability set is not wanted.

Every Anvil plugin also provides `anvilLogin` and `anvilLogout` for the optional online-player
authentication profile flow.

Authentication belongs to the selected protocol provider. A provider can expose a
`ProtocolAuthentication` service; offline-only providers need no login implementation. The Gradle
tasks resolve providers from `anvilProtocols` and do not construct a client backend to authenticate:

```shell
./gradlew anvilLogin --auth-profile=main
./gradlew anvilLogout --auth-profile=main
```

Use `anvil { protocol("your-provider") }` when several providers are installed. Without a selection,
the sole provider is used. A provider without interactive authentication reports that directly.
MCProtocol supplies the Microsoft device-code workflow and retains its private profile store.
The option is `--auth-profile`; Gradle reserves `--profile` for its own build profiler.

Apply Anvil where the scenarios live. The umbrella plugin includes the built-in capability set, but
platforms are always explicit:

```kotlin
plugins {
    java
    id("me.whereareiam.anvil") version "0.0.1"
    id("me.whereareiam.anvil.platform.paper") version "0.0.1"
}

dependencies {
    add("anvilProtocols", anvil.protocols.mcprotocol)
}

anvil {
    acceptEula()
    protocol("mcprotocol")
    artifact("plugin-under-test", project(":plugin"))
}
```

The EULA setting records your acceptance of the
[Minecraft EULA](https://www.minecraft.net/eula). Anvil can start five servers before breakfast,
but it will not accept legal agreements on your behalf.

The plugin creates a dedicated `anvil` source set under `src/anvil`. Production code stays in
`src/main`, ordinary unit tests stay in `src/test`, and server scenarios get their own little
dimension. In a single-project build, register the artifact from `tasks.named("jar")` instead of
`project(":plugin")`.

For direct embedding without the Gradle plugin, use
`me.whereareiam.anvil:launcher`; do not depend on `engine` directly. The launcher distribution
contains the executable composition root and public contracts. Protocol and platform providers are
selected runtime dependencies, so the launcher itself does not impose MCProtocolLib or a platform
SDK on consumers. Add the selected provider and matching platform-agent artifacts to the embedding
application's runtime classpath, then set `EngineOptions.protocolId` when more than one protocol
provider is installed.
`EngineOptions` lives in `me.whereareiam.anvil.engine.model`.

For another tooling integration that needs the foreground shell, use
`me.whereareiam.anvil:tooling-runner`. It exposes `AnvilRunner` and `AnvilRunnerConfiguration`
without a Gradle API dependency; the Anvil Gradle plugin is one adapter that supplies the
configuration and consumer runtime classpath.

With the Gradle plugin, Anvil ships MCProtocolLib as a ready-to-use provider. Add the provider and
select the protocol when more than one is present. Apply one platform unit for every platform used by
the scenario:

```kotlin
dependencies {
    add("anvilProtocols", anvil.protocols.mcprotocol)
}

anvil {
    protocol("mcprotocol")
}
```

Apply the platform units alongside the Anvil plugin:

```kotlin
plugins {
    id("me.whereareiam.anvil") version "0.0.1"
    id("me.whereareiam.anvil.platform.paper") version "0.0.1"
    id("me.whereareiam.anvil.platform.velocity") version "0.0.1"
}
```

Each unit owns its provider and matching agent artifacts. The selected agent is installed
automatically into the managed workspace; consumers do not configure agent ports, tokens, or plugin
paths.

Platform agents discover their transport through `AgentServerProvider` from `agent-api`. Their
platform code depends on the agent contracts; the packaged runtime supplies the authenticated
loopback implementation. Host integrations use `AgentClient` for typed operations. The lower-level
`AgentConnection` accepts request and response models and delegates serialization to its transport.
The runtime classpath must supply the packaged platform-agent JAR. Anvil does not select artifacts
by scanning a neighboring `build/libs` directory.

External platform-agent operations implement `AgentOperationProvider` and register typed
`AgentOperation` descriptors and handlers through `AgentOperationRegistry`. Install their JARs as
workspace assets under `plugins/anvil-agent-extensions`; the platform agent loads their service
descriptors automatically. Host capability providers obtain `AgentDirectory` through
`PlayerCapabilityContext.requireService` and invoke those operations through the borrowed agent
clients. Native APIs and platform-thread execution are exposed by `PlatformAgent.findService` and
`PlatformAgent.call`. See [the complete extension workflow](docs/content/extending/agent-operations/index.md).

### Prebuilt Spigot

Spigot uses prebuilt JARs from the third-party [GetBukkit supplier](https://getbukkit.org/download/spigot).
Anvil does not run BuildTools or compile Spigot. GetBukkit addresses releases by Minecraft version,
so remote Spigot declarations pin the executable's SHA-256 instead of a BuildTools build number:

```java
MinecraftServer.builder()
        .name("server")
        .platform(Platforms.SPIGOT)
        .distribution(Distribution.pinned(
                "1.21.11",
                "6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd"
        ))
        .build();
```

The compatibility catalog uses these pins, retrieved from GetBukkit on 2026-09-05:

| Minecraft version | Spigot JAR SHA-256 |
|---|---|
| `1.18.2` | `6996f8025b497dd32271b09b6624b26b95d0e39dc9f6860d01fd0db38905076d` |
| `1.19.4` | `beb43043e8d03d1d4557755102aebc0bfe89368a594939fb1db5f9e5ee84ebb3` |
| `1.20.6` | `d9aab418f8404148b47c8faba484698a526f121f9fb5412264de9dc4b65db02f` |
| `1.21.11` | `6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd` |
| `26.1.2` | `95f871fd6d055ba10b5a058768ddad43b0be0286480c8eba435c835c95d5f19c` |
| `26.2` | `b9295ba3fd4c9d75e361122ff89b7978b2afbe5538e60d7fa5cd7dcd96a355b1` |

Downloads are verified and cached under
`~/.anvil/distributions/getbukkit/spigot/<version>/<sha256>/spigot-<version>.jar`.
Changed supplier bytes fail checksum verification; Anvil never changes a pin automatically or falls
back to a local build. Local and named server artifacts remain supported and require
`minecraftVersion`. Existing BuildTools cache files remain untouched and are not reused by this supplier.
Migrate old Spigot `Distribution.remote(version, build)` declarations to `Distribution.pinned(version, sha256)`.
Paper, Velocity, and BungeeCord retain their existing build selectors.

### Add another protocol provider

Protocols are providers, not engine branches. A protocol module implements `ProtocolProvider` and
`ProtocolBackend` from `protocol-api`, registers its provider with
`META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider`, and contributes its own
native client runtime and support catalog. Add that module to `anvilProtocols` and select its stable
ID with `anvil { protocol("your-protocol") }`. The engine discovers it through the SPI alongside
`mcprotocol`; no launcher or engine refactor is required.

The engine selects the provider once before discovering capabilities, including when selection is
automatic. Capability providers are then filtered using that resolved ID. Ambiguous or unknown
selections fail before any backend or process starts. Backend instances are created after scenario
validation and owned by the engine until it closes.

Public capability interfaces such as `Session` and `Movement` remain portable. Our packet adapters
are explicitly named `McProtocolSessionProvider`, `McProtocolMovementAdapter`, and so on; they target
MCProtocolLib. Another backend provides its own adapters for the same APIs through its own public
execution-service contracts. See [external provider conformance](docs/content/extending/protocol-providers/index.md).

Protocol extension packages are grouped under `protocol.api.provider`, `protocol.api.player`,
`protocol.adapter.api.capability`, and `protocol.adapter.api.player` (all prefixed by
`me.whereareiam.anvil`). Extensions using the former flat packages must update imports and service
descriptor filenames and recompile; existing extension JARs are not binary-compatible. Maven IDs
and worker operation/event names remain unchanged. See the
[package migration guide](docs/content/extending/protocol-providers/index.md#package-migration).

MCProtocol worker adapters register namespaced operations such as `combat.attack`; `create`,
`destroy`, and `shutdown` are reserved for internal lifecycle control. Use the public adapter API,
not the backend's private JSON envelopes. See [custom capabilities](docs/content/extending/capabilities/index.md).

### Select only the capabilities you need

The umbrella plugin includes `default`, the aggregate of all built-in capabilities. For a smaller
compile and runtime surface, use `me.whereareiam.anvil.scenarios` and apply individual capability
units:

```kotlin
plugins {
    id("me.whereareiam.anvil.scenarios") version "0.0.1"
    id("me.whereareiam.anvil.capability.session") version "0.0.1"
    id("me.whereareiam.anvil.capability.messages") version "0.0.1"
    id("me.whereareiam.anvil.capability.server") version "0.0.1"
}
```

The available built-in unit IDs are `session`, `server`, `messages`, `movement`, `inventory`, and
`interaction`. The umbrella plugin uses the separate `default` unit internally to include the
aggregate artifact. Unit plugins own their capability artifact coordinates; the scenarios plugin has
no built-in capability catalog.

An external capability is installed through the same bucket:

```kotlin
dependencies {
    anvilCapabilities("com.example:combat:1.4.0")
}
```

Omit the block entirely when no simulated-player behavior is needed. A scenarios-only project can
then start a real environment for a developer to join. A JUnit-only project can test process,
artifact, or server lifecycle behavior without installing `Session` or any other player capability.

### Describe the mess once

Create `src/anvil/java/com/example/test/PaperScenario.java`:

```java
package com.example.test;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.AssetInstallMode;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.CleanupPhase;
import me.whereareiam.anvil.api.type.WorkspaceMode;

import java.nio.file.Path;

public final class PaperScenario implements AnvilScenarioDefinition {
    @Override
    public AnvilScenario define() {
        MinecraftServer paper = MinecraftServer.builder()
                .name("server")
                .platform(Platforms.PAPER)
                .distribution(Distribution.remote("1.21.11", "132"))
                .workspace(WorkspacePlan.builder()
                        .mode(WorkspaceMode.PERSISTENT)
                        .asset(WorkspaceAsset.builder()
                                .group("plugin")
                                .source(AssetSource.artifact("plugin-under-test"))
                                .target(Path.of("plugins", "plugin-under-test.jar"))
                                .mode(AssetInstallMode.ALWAYS)
                                .build())
                        .cache(WorkspaceCache.builder()
                                .group("plugin-libraries")
                                .path(Path.of("plugins", "ExamplePlugin", ".libraries"))
                                .key("example-plugin-libraries")
                                .policy(CachePolicy.RESTORE_AND_SAVE)
                                .build())
                        .cleanup(WorkspaceCleanup.builder()
                                .group("generated")
                                .path(Path.of("logs", "previous-run"))
                                .phase(CleanupPhase.BEFORE_START)
                                .build())
                        .build())
                .memoryMegabytes(1024)
                .build();

        return AnvilScenario.builder()
                .name("paper-1.21.11")
                .entrypoint(paper.getName())
                .server(paper)
                .build();
    }
}
```

That class defines the environment. It does not reserve three players, predict their names, or ask
how many friends you have. Players belong to the running test and are created when needed.

### Let Alice take over

```java
package com.example.test;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.runtime.AnvilContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.junit.AnvilTest;
import org.junit.jupiter.api.Test;

final class PluginTest {
    @Test
    @AnvilTest(PaperScenario.class)
    void reportsThatItIsReady(AnvilContext anvil) {
        SimulatedPlayer alice = anvil.players().create("Alice");
        Session session = alice.capability(Session.class);
        Messages messages = alice.capability(Messages.class);

        session.connect();
        session.connected();

        messages.command("myplugin status");
        messages.received("Ready");
    }
}
```

Run it:

```shell
./gradlew anvilTest
```

Anvil provisions the server, installs the asset, restores declared caches, waits until everything is ready, connects
Alice, runs the test, and cleans up afterward. Alice never asks whether this can wait until tomorrow.

Assets, caches, and cleanup are separate declarations. Asset targets are process-workspace-relative:
file sources are copied to the target file and directory sources copy their contents into the target
directory. `SEED_ONCE` only installs into an empty persistent target; `ALWAYS` refreshes it on every
start. Cache paths are also relative and are stored below Anvil's global cache directory. Paper and
Velocity may contribute safe default caches such as `libraries/`; a scenario can add a custom cache,
replace a default by path, or disable it with `CachePolicy.DISABLED`. Cleanup declarations never imply
cache or asset behavior, and overlapping cache/cleanup paths are rejected.

## Alice starts with a toolkit, not a ceiling

`SimulatedPlayer` is not one enormous interface Anvil must keep feeding forever. A capability is a
global Anvil API concept, while a capability provider is only the runtime mechanism that supplies
its implementation. Anvil ships a useful starting set:

| Built-in capability | What your test can do                                                          |
|---------------------|--------------------------------------------------------------------------------|
| `Session`           | Connect, disconnect, rejoin, inspect connection state, and verify kick reasons |
| `Messages`          | Send chat and commands, capture messages, and wait for expected output         |
| `Server`            | Observe usernames, UUIDs, proxies, backends, and server transfers              |
| `Movement`          | Send position, rotation, and on-ground state                                   |
| `Inventory`         | Inspect containers, select held slots, and click inventory slots               |
| `Interaction`       | Use held items and interact with blocks or entities                            |

That table describes what Anvil includes, not everything Alice will ever be allowed to learn. An
external library can ship another capability wiring bundle as a normal dependency. Anvil discovers
its provider, attaches the typed capability to compatible players, and exposes it through the same
global API:

```java
Combat combat = alice.capability(Combat.class);
```

No core fork. No giant player interface. No waiting for the next Anvil release because somebody
invented a packet you care about.

The built-in observations wait for a sensible timeout and fail with recent history when the
expected event never arrives. Add a `Duration` when your server needs special treatment.

Providers can retrieve only the capability dependencies declared in their `CapabilityDescriptor`.
Registered cleanup runs in reverse order, including when capability construction fails. Cleanup
failures are reported after the remaining resources are released.

The `Server` capability uses the scenario's player-observation service and has no `Session`
dependency. Its wiring does not install the session adapter. Agent operations can be used with a
different backend or without any packet-capability adapter.

Players can be created dynamically, disconnected, rejoined, or destroyed independently. Installed
capabilities decide what else they can do.

### Build an external capability without leaking its backend

A capability family keeps its public contract, optional independent implementation, protocol
adapter, and consumer wiring separate:

For an external family, use the capability owner's Maven group and stable artifact suffixes such as
`com.example.anvil:combat-api`, `com.example.anvil:combat-mcprotocol`, and
`com.example.anvil:combat`. Keep public Java types under `com.example.anvil.combat` and adapter
classes under `com.example.anvil.combat.internal`. The `me.whereareiam.anvil` group is reserved for
Anvil's own artifacts.

```text
combat-api
  -> anvil-api

combat-common                 (optional independent provider implementation)
  -> anvil-api
  -> combat-api
  -> capability-api

combat-mcprotocol             (MCProtocolLib adaptation)
  -> anvil-api
  -> combat-api
  -> capability-api
  -> protocol-adapter-api

combat                        (source-free wiring bundle)
  api          -> combat-api
  runtimeOnly  -> exactly one of combat-common or combat-mcprotocol
```

`combat-mcprotocol` never depends on `combat-common`, and neither implementation depends on another
implementation or wiring module. Both may exist as alternative wiring choices, but they must not
register two providers for the same `Combat` type. If common code must be shared by multiple
providers, put its contract in a separate `combat-common-api` and compose it through a dedicated
provider without making `combat-mcprotocol` depend on the common implementation.
`protocol-adapter-api` carries opaque backend packet values and has no MCProtocolLib dependency.
`combat-mcprotocol` adds MCProtocolLib directly for its binding. Because the consumer-facing `combat`
artifact installs the selected adapter with `runtimeOnly`, ordinary test code sees `Combat` but not
protocol or provider machinery.

The complete module graph and enforced dependency rules are documented in
[Architecture](docs/content/contributing/architecture/index.md).

CI behavior, live-test approval, development publication, and release publication are documented in
[Building and publication](docs/content/contributing/publishing/index.md).

## One server would have been too reasonable

A scenario can launch a proxy and multiple backends as one supervised environment. Players connect
through the declared entrypoint while the test observes where they land, which identity each server
sees, and whether transfers actually happened.

Velocity with Paper. Velocity with Spigot. BungeeCord with Paper. BungeeCord with Spigot. Modern
forwarding, legacy forwarding, multiple backends, real processes. All the configuration normally
spread across several directories and one developer's increasingly fragile patience.

Providers declare supported identity-forwarding modes. Anvil negotiates one mode for each connected
proxy/server group before launch, including overlapping proxy routes, and supplies shared per-run
forwarding settings to each provider. Incompatible groups fail preflight; provider implementations
do not select forwarding by inspecting another platform's name.

Platform configuration writers parse existing YAML or TOML, preserve unrelated values, and replace
runtime-owned settings structurally. Repeated configuration does not append duplicate YAML sections,
and direct-server runs disable forwarding retained in persistent workspaces. Serialization rewrites
formatting and comments. Malformed configuration and duplicate YAML keys fail with diagnostics.

Velocity `.setting(key, value)` values use TOML literal syntax: `"128"` represents an integer,
`"true"` a boolean, `"[1, 2]"` an array, and `"'hello'"` a string. Dotted keys update nested tables;
runtime-owned roots such as `servers` cannot be overridden. Quote string values that previously
relied on implicit string detection. BungeeCord settings use YAML values; server properties remain
plain strings.

## Humans may still enter

Sometimes you genuinely want to join the server. Fine. Run the same scenario in the foreground:

```shell
./gradlew anvilScenario --list
./gradlew anvilScenario --scenario=manual-paper
./gradlew anvilScenario --group=development
```

Anvil prints the vanilla-client join address and gives you status, logs, restarts, scenario
switching, and routed console commands. Manual testing remains available; it simply stops being the
entire quality strategy.

## What it actually supports

| Role                     | Platforms            |
|--------------------------|----------------------|
| Direct or backend server | Paper, Spigot        |
| Proxy                    | Velocity, BungeeCord |

Minecraft `1.18.2`, `1.19.4`, `1.20.6`, `1.21.11`, `26.1.2`, and `26.2` are supported natively across
direct servers and every supported proxy/backend combination. The matrix covers 36 routes, plus
capability and reconnect/identity checks for each version. Versions 1.16.5 and 1.17.1 are excluded
because reproducible published protocol artifacts were not available from the supported sources.
Distributions may come from pinned remote builds, local JARs, Maven artifacts, or Gradle project artifacts.

The host framework and protocol workers require Java 21. Older Spigot runs with Java 17 within
its upstream JVM limits; Bukkit agents target Java 17. Minecraft 26.* servers use Java 25.
The provider's declared Java range controls selection—Anvil does not bypass upstream version checks.

Anvil uses native Java protocol clients and local JVM processes. Docker, Kubernetes, hosted
orchestration, Fabric, Sponge, and Bedrock are not supported yet. Pretending otherwise would make
the feature list longer, but the software would remain exactly the same.

## Distrust the README

Good instinct. The repository contains an executable authentication use case:

- [The plugin](examples/proof-of-patience/src/main/java/me/whereareiam/anvil/example/patience/plugin/ProofOfPatiencePlugin.java), which accepts patience where credentials would usually go
- [Its ordinary unit test](examples/proof-of-patience/src/test/java/me/whereareiam/anvil/example/patience/plugin/ReconnectChallengeTest.java)
- [Its reusable scenario catalog](examples/proof-of-patience/src/anvil/java/me/whereareiam/anvil/example/patience/scenario/ProofOfPatienceScenarios.java)
- [The release journey Alice performs instead of you](examples/proof-of-patience/src/anvil/java/me/whereareiam/anvil/example/patience/journey/ProofOfPatienceJourneyTest.java)

The example tests plugin behavior. Anvil's cross-module checks live in
[`anvil-testing`](anvil-testing/README.md): `testing-runtime` covers discovery and composition,
`testing-server` covers real platform sessions and routing, and `testing-fixtures` contains the
server-plugin and external-extension JAR modules. All use ordinary `src/test` or `src/main` layouts.

```shell
./gradlew :anvil-testing:testing-runtime:test
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full
```

Ordinary builds skip the entire server-test task. Full mode enables it. Consumer journeys run
separately after `./gradlew publishToMavenLocal`, using
`./gradlew -p examples/proof-of-patience anvilTest`. The old grouping-module task
`:anvil-testing:test` is replaced by the two explicit module tasks above.

## License

Anvil is available under the [Apache License 2.0](LICENSE). Break your plugin, not the license.
