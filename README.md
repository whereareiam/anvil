# Anvil

The Minecraft test framework that got tired of watching you test everything by hand.

[Documentation](https://anvil.whereareiam.me)

Anvil tests whatever runs on a Minecraft server: plugins, mods, server and proxy software itself. It
gives your tests control over:

- real server and proxy processes, running the exact JARs, plugins and mods your users will install
- lightweight clients speaking the real Minecraft protocol
- complete environments of servers and proxies, which can also stay open for a developer to join

Nothing in it is a closed list. Platforms, the players' abilities, protocol libraries and the way tests
run are all extension points: Anvil ships Paper, Spigot, NeoForge, Velocity and BungeeCord, a set of
built-in capabilities and a JUnit integration, and your own library can add more without forking it.

No mocked server. No full game client. No small orchestra of terminals waiting for you to conduct.
The only fake part is Alice, and she is far more consistent than you are after the twentieth
reconnect.

## Why

Testing a change once is easy: build the JAR, start the server, join, recreate the condition, look at
chat. Then a permission check changes, or a dependency, or the platform, or the Minecraft version, and
it is back to step one. Multiply that by every player journey your plugin, mod or server has, new and
returning players, kicks, reconnects, proxy transfers, restarts, and by every supported version, and
"we tested it" quietly becomes "we remembered most of it."

With Anvil you describe the environment once, let simulated players walk the journey, and check what
the players and servers actually observed. The same journey runs after every change, against any
supported version, and keeps the process logs when it fails.

## What Anvil tests against

- **The real server, not a model of it.** Your code runs in the actual server or proxy process, with
  its scheduler, its event order, its other plugins or mods and its quirks, so a test passes only when
  the real thing works.
- **Both sides of the connection.** Anvil puts its own agent inside every server and proxy it starts.
  A test asks the player what it saw and the server what it knows: who is online and as whom, which
  backend a proxy sent them to, which world they are in. Your own extension can teach the agent more,
  so a test can check your plugin's or mod's state from inside the server, not only through chat.
- **Real players.** Players are clients that speak the game's protocol and move like an ordinary game
  client, log in through proxies, get kicked and reconnect, and see what a player would see.
- **Every version you support.** The same test runs on old and new Minecraft versions, on a single
  server and behind a proxy, and gives the same answer on every machine.
- **Whole environments.** A test can span a proxy and several servers, and the same scenario opens for
  a developer to join when a human eye is needed.
- **Failures you can read.** When a wait fails, Anvil says what it saw instead and keeps the server
  logs of the failed run.

Fast unit tests of your logic stay where they are; Anvil covers what only a running server can tell you.

## Quick start

Anvil needs Java 21 or newer. Add the repositories to `settings.gradle.kts`:

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
    }
}
```

Apply Anvil in the project you test, in `build.gradle.kts`. The example tests a Paper plugin with
JUnit; a mod, a server JAR or a proxy plugin is registered and installed the same way:

```kotlin
plugins {
    java
    id("me.whereareiam.anvil.junit") version "0.0.1"
    id("me.whereareiam.anvil.capability.default") version "0.0.1"
    id("me.whereareiam.anvil.platform.paper") version "0.0.1"
}

dependencies {
    add("anvilRuntimeOnly", "me.whereareiam.anvil:protocol-mcprotocol:0.0.1")
}

anvil {
    acceptEula()
    artifact("plugin-under-test", tasks.named("jar"))
}
```

`acceptEula()` records your acceptance of the [Minecraft EULA](https://www.minecraft.net/eula). Anvil
can start five servers before breakfast, but it will not accept legal agreements on your behalf.

Describe the environment in `src/anvil/java`:

```java
public final class PaperScenario implements AnvilScenarioDefinition {
    @Override
    public AnvilScenario define() {
        MinecraftServer paper = MinecraftServer.builder()
                .name("server")
                .platform(Platforms.PAPER)
                .distribution(Distribution.remote("1.21.11", "132"))
                .workspace(WorkspacePlan.builder()
                        .asset(WorkspaceAsset.builder()
                                .source(AssetSource.artifact("plugin-under-test"))
                                .target(Path.of("plugins", "plugin-under-test.jar"))
                                .build())
                        .build())
                .build();

        return AnvilScenario.builder().name("paper").entrypoint("server").server(paper).build();
    }
}
```

And let Alice do the boring part:

```java
final class PluginTest {
    @Test
    @AnvilTest(PaperScenario.class)
    void reportsThatItIsReady(ScenarioContext anvil) {
        SimulatedPlayer alice = anvil.players().create("Alice");
        alice.capability(Session.class).connect();

        Messages messages = alice.capability(Messages.class);
        messages.command("myplugin status");
        messages.received("Ready");
    }
}
```

```shell
./gradlew anvilTest
```

Anvil downloads the pinned server, installs your plugin, waits until everything is ready, connects
Alice, runs the test and cleans up. [Your first test](https://anvil.whereareiam.me/docs/dev/getting-started/first-test)
has the same files with their imports, and [Getting started](https://anvil.whereareiam.me/docs/dev/getting-started)
covers the rest: choosing plugins, workspaces and caches, accounts, and embedding Anvil without Gradle.

## What Alice can do

Players, servers and proxies get their abilities from capabilities, typed APIs you ask for by class. The
built-in set lets a player connect and reconnect, chat and run commands, move with the vanilla client's
physics, use inventories, interact with blocks and entities, answer dialogs and cookies, and wait for
titles, sounds, entities and blocks. Servers and proxies can run console commands and report which
players they see and where a proxy sent them, and a NeoForge server waits in its own ticks until a condition
holds, as fast as it can run them when the test asks.

Your own library can add more as an ordinary dependency, without forking Anvil:
[Capabilities](https://anvil.whereareiam.me/docs/dev/building-blocks/players/capabilities) lists the
built-in ones, and [Extending capabilities](https://anvil.whereareiam.me/docs/dev/extending/capabilities) shows how to write one.

## Run it your way

JUnit is the standard way to run scenarios automatically (`@AnvilTest`, `./gradlew anvilTest`), not the
only one. The same scenarios run in the foreground, in the IntelliJ plugin, and in your own application
through the [launcher](https://anvil.whereareiam.me/docs/dev/integrations/embedding), and the
[tooling](https://anvil.whereareiam.me/docs/dev/extending/tooling) lets another tool drive them.

## Real environments, not one server

A scenario can start a proxy and several backends as one environment, with forwarding negotiated and
configured for you, and check where each player lands and which identity each server sees.

When you do want to look around yourself, run the same scenario in the foreground:

```shell
./gradlew anvilRun --scenario=paper
```

Anvil prints the join address and gives you logs, restarts and the server consoles. The
[IntelliJ plugin](https://anvil.whereareiam.me/docs/dev/integrations/intellij/installation) does the same
from a tool window.

## Built in, and what you can add

| Role                     | Built-in platforms   |
|--------------------------|----------------------|
| Direct or backend server | Paper, Spigot        |
| Direct server            | NeoForge             |
| Proxy                    | Velocity, BungeeCord |

Players speak Minecraft 1.16.5 to 26.3 through the built-in MCProtocolLib library. The
[version support page](https://anvil.whereareiam.me/docs/dev/building-blocks/environments/platforms/versions)
lists every release, which ones the live matrix runs, and which Java each server gets.

Another server or proxy software is a [platform provider](https://anvil.whereareiam.me/docs/dev/extending/platform-providers),
another client library a [protocol library](https://anvil.whereareiam.me/docs/dev/extending/protocol-libraries), and an action
inside a running server an [agent operation](https://anvil.whereareiam.me/docs/dev/extending/agent-operations), each shipped as an
ordinary dependency. [Extending Anvil](https://anvil.whereareiam.me/docs/dev/extending) covers them all.

## See it work

[`examples/proof-of-patience`](examples/proof-of-patience) is a complete plugin with its unit test,
its scenarios, and the release journey Alice performs instead of you.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Anvil is available under the [Apache License 2.0](LICENSE). Break your plugin, not the license.
