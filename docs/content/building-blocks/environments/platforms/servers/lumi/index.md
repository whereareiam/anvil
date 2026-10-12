---
title: Lumi
description: Run a Lumi Bedrock Edition server from a pinned Lumi release and drive it through its console and agent.
---

Use Lumi to run a Bedrock Edition server. [Lumi](https://github.com/KoshakMineDEV/Lumi) is a Nukkit-based
server that implements the Bedrock protocol itself; it runs on Java and loads Nukkit plugins. Apply
`me.whereareiam.anvil.platform.lumi` at the same version as your main Anvil plugin. The unit includes the
Lumi agent, a small plugin that Anvil installs for native server observations and console commands.

## Select a Lumi release

A remote distribution names the Bedrock version and, as its build, the Lumi release. Add this
declaration fragment to your scenario definition:

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;

MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.LUMI)
		.distribution(Distribution.remote("1.26.50", "1.6.9"))
		.build();
```

Add `server` to the scenario and use its name as the entrypoint. Lumi names every Bedrock version
`1.x.y`, so Bedrock `26.10` is `1.26.10`. Use the version that names the protocol: Bedrock `1.20.1`
speaks the protocol of `1.20.0`, so declare `1.20.0`. The
[versions page](../../versions/index.md#bedrock-edition) lists every version Anvil knows. A release that
does not accept the version, a release Anvil's Lumi data does not list, and `latest` are refused before
anything is downloaded.

Anvil downloads `com.koshakmine:Lumi:<release>` from Lumi's Maven repository,
`https://repo.lumi.su/releases`, and verifies it against the SHA-256 that repository publishes for the
release. To pin the JAR yourself, add the checksum:

```java
Distribution pinned = Distribution.builder()
		.version("1.26.50")
		.build("1.6.9")
		.sha256("<64-digit SHA-256 of Lumi-1.6.9.jar>")
		.build();
```

The JAR is cached under `<cacheDirectory>/distributions/lumi/<release>`.

## One protocol per server

Lumi can accept several Bedrock protocols at once. Anvil allows exactly the protocol of the declared
version, so the server is the version you declared and a player of another version is refused at login.

Anvil knows a Lumi version only when Lumi and the CloudburstMC Protocol library speak it with the same
protocol, so a player can join every Lumi server Anvil runs. Bedrock `1.26.45` is not one of them: Lumi
1.6.9 speaks it as protocol 2170 and accepts 2169 only as its own `1.26.44`, while CloudburstMC Protocol
speaks `1.26.45` as 2169. Declare `1.26.40` or `1.26.50` instead.

## Player identity

Lumi shows a player to clients under the UUID its login named, and that is the UUID the player is
granted. For a player that logs in offline, Lumi keeps a UUID of its own, derived from the name or taken
from a name it has seen before; with Xbox authentication the two are the same. The Lumi agent finds the
player by the granted UUID and reports Lumi's own, so the Server capability's observed profile carries
Lumi's UUID while the granted profile carries the one the client sees.

## Configure the server

Lumi keeps its settings in `settings.yml`. A server's `.setting(key, value)` entries name a dotted path
into that file and give a YAML value:

```java
MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.LUMI)
		.distribution(Distribution.remote("1.26.50", "1.6.9"))
		.setting("world.spawn-protection", "4")
		.setting("player.default-gamemode", "1")
		.build();
```

Anvil writes the file before every start and keeps values it does not manage. Unless a setting replaces
them, it sets `general.motd`, `general.language` to `eng`, `world.spawn-protection` to `0`,
`network.enable-query` and `bstats.enable` to `false`. It always sets `general.server-ip`,
`general.server-port`, `general.multiversion.min-protocol` and `max-protocol`, `network.xbox-auth` from
`.onlineMode(...)`, and `network.rcon.enable` to `false`; a setting for one of these is refused.

Install plugins into `plugins/` with [workspace assets](../../../workspaces/index.md). Lumi converts an
old `server.properties` into `settings.yml` and deletes it, so configure Lumi through settings only.

## Use your own JAR

Use `Distribution.local(path)` or `Distribution.artifact(name)` with a Lumi server JAR and declare
`minecraftVersion` on the server, which selects the protocol Anvil allows. See
[distribution selection](../../../provisioning/platform/index.md) for registration and invocation.

## Java

Lumi runs on Java 21; see
[Java per platform version](../../../provisioning/java/index.md#java-per-platform-version).

## Limitations

- Simulated players join Lumi through the
  [CloudburstMC Protocol library](../../versions/index.md#bedrock-players-through-cloudburstmc-protocol),
  whose built-in player capabilities so far are Session, Server, Messages, Movement and Inventory, which
  only observes. The live matrix drives Lumi through the
  [process console](../../../actions/console/index.md) and the [agent](../../../actions/agents/index.md)'s
  command execution, and observes a Bedrock player through Session and Server.
- Lumi's game listener is UDP. Local and Docker execution reserve and publish it as UDP; the agent
  connection stays TCP.
- Lumi has no identity forwarding and Anvil ships no Bedrock proxy, so a Lumi server cannot be a proxy
  backend.
- Lumi verifies online logins with Xbox Live only; `sessionServer` is refused on a Lumi server.
- Lumi starts no Mojang code and writes no `eula.txt`, but Anvil still requires `acceptEula()` before it
  starts any server.
