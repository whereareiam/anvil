---
title: Versions and compatibility
description: Find the Minecraft versions each protocol library and platform supports, how Anvil assesses them, and how to run a version Anvil does not ship.
---

Choose one native Minecraft version for every server reachable by a player. A proxy has its own
release version; that version does not select the simulated player's protocol.

Anvil assesses players and processes separately. A player speaks one Minecraft version of one edition
through a release of a protocol library. A server or proxy runs one platform version on one Java version.
Each assessment has a [support level](#support-levels-and-policies), and the support policy decides
which levels run.

## Players through MCProtocolLib

Anvil bundles two protocol libraries: MCProtocolLib for Java Edition servers and
[CloudburstMC Protocol](#bedrock-players-through-cloudburstmc-protocol) for Bedrock Edition servers.
MCProtocolLib has the ID `mcprotocol`. Its releases are data:
`mcprotocol-releases.toml`, shipped inside `protocol-mcprotocol`. Each release names the Minecraft
edition it implements (`java` for every MCProtocolLib release), speaks one wire protocol, and lists
every Minecraft version that speaks it; its highest version is the release key.

| Release key | MCProtocolLib release | Protocol | Minecraft versions | Verified by the live matrix |
|---|---|---|---|---|
| `1.18.2` | `1.18.2-1` | 758 | `1.18.2` | `1.18.2` |
| `1.21.1` | `1.21-20241010.155958-24` | 767 | `1.21`, `1.21.1` | `1.21.1` |
| `1.21.11` | `1.21.11-20260512.221357-18` | 774 | `1.21.11` | `1.21.11` |
| `26.1.2` | `26.1-20260708.090514-22` | 775 | `26.1`, `26.1.1`, `26.1.2` | `26.1.2` |

The last three releases are MCProtocolLib snapshot builds, pinned to exact timestamps. A player uses the
release that lists its exact Minecraft version, never a neighboring one, so a version that no release
lists, such as `1.20.6` or `1.21.10`, has no player support even when the server platform runs it.
Every built-in player capability is installed on every release in the table.

Each player's worker downloads its release's runtime JARs and checks them against their recorded
SHA-256 pins. A release with an artifact that has no pin is listed but cannot be launched: preparing a
scenario whose servers would use it by default prints a warning, and creating a player that selects
it is refused with the reason.

## Bedrock players through CloudburstMC Protocol

CloudburstMC Protocol has the ID `cloudburst`, and its releases are data too:
`cloudburst-releases.toml`, shipped inside `protocol-cloudburst`. Its one release names the `bedrock`
edition and speaks every listed Bedrock version through that version's own codec, so it maps each
version to its own protocol number. Versions use the numbering CloudburstMC Protocol and Bedrock servers
such as Lumi share: Bedrock 26.x is written `1.26.x`.

| Release key | CloudburstMC Protocol release | Verified by the live matrix |
|---|---|---|
| `1.26.60` | `3.0.0.Beta14-20261009.172637-6` | None yet |

| Minecraft versions | Protocols |
|---|---|
| `1.20.0`, `1.20.10`, `1.20.30`, `1.20.40`, `1.20.50`, `1.20.60`, `1.20.70`, `1.20.80` | 589, 594, 618, 622, 630, 649, 662, 671 |
| `1.21.0`, `1.21.2`, `1.21.20`, `1.21.30`, `1.21.40`, `1.21.50`, `1.21.60`, `1.21.70`, `1.21.80` | 685, 686, 712, 729, 748, 766, 776, 786, 800 |
| `1.21.90`, `1.21.93`, `1.21.100`, `1.21.111`, `1.21.120`, `1.21.124`, `1.21.130` | 818, 819, 827, 844, 859, 860, 898 |
| `1.26.0`, `1.26.10`, `1.26.20`, `1.26.30`, `1.26.40`, `1.26.45`, `1.26.50`, `1.26.60` | 924, 944, 975, 1001, 2168, 2169, 2193, 2225 |

Every listed version is `COMPATIBLE`. The release is a CloudburstMC Protocol snapshot build, pinned to
an exact timestamp like the MCProtocolLib snapshots. Its players log in offline with a self-signed
identity, which a server accepts only with Xbox authentication turned off, or online with an
[Xbox account](../../../players/authentication/index.md). Bedrock has no handshake host, so a Bedrock
player that declares a virtual host is refused when it connects. Session is the only built-in player
capability with a CloudburstMC Protocol side so far; requesting another built-in protocol capability
on a Bedrock player reports the libraries that provide it.

## Servers and proxies

| Platform | Supported versions | Verified by the live matrix |
|---|---|---|
| Paper | `1.16.5` and newer | `1.18.2`, `1.21.1`, `1.21.11`, and `26.1.2` |
| Spigot | `1.16.5` and newer | `1.21.11` and `26.1.2` |
| NeoForge | `1.21.1` and newer | `1.21.1`, `1.21.11`, and `26.1.2` |
| Lumi | Bedrock `1.20.0` to `1.26.50`, as listed [below](#bedrock-edition) | `1.26.50` |
| Velocity | `3.3.0` and newer | `3.5.1` |
| BungeeCord | Every build, assessed by Java range only | None; the matrix runs build `2085` |

Each platform provider ships its Minecraft edition, its known versions, its Java rows, its agent's
minimum Java, and the combinations Anvil's live matrix verifies as data. Lumi declares the `bedrock`
edition and every other platform in the table the `java` edition. Planning picks one exact LTS release
per process.
See [Java per platform version](../../provisioning/java/index.md#java-per-platform-version) for the
rows, the defaults, and the Java version of each verified combination.

### Bedrock Edition

Lumi serves Bedrock Edition. Lumi names every Bedrock version `1.x.y`, so Bedrock `26.10` is `1.26.10`,
and Anvil follows that naming. Each version below is the first of its protocol: a patch release that
speaks the same protocol, such as `1.20.1`, is declared as the version that names it, `1.20.0`. A Lumi
server allows exactly the protocol of its version.

| Lumi release | Bedrock versions (protocol) | Verified by the live matrix |
|---|---|---|
| `1.6.9` | `1.20.0` (589), `1.20.10` (594), `1.20.30` (618), `1.20.40` (622), `1.20.50` (630), `1.20.60` (649), `1.20.70` (662), `1.20.80` (671), `1.21.0` (685), `1.21.2` (686), `1.21.20` (712), `1.21.30` (729), `1.21.40` (748), `1.21.50` (766), `1.21.60` (776), `1.21.70` (786), `1.21.80` (800), `1.21.90` (818), `1.21.93` (819), `1.21.100` (827), `1.21.111` (844), `1.21.120` (859), `1.21.124` (860), `1.21.130` (898), `1.26.0` (924), `1.26.10` (944), `1.26.20` (975), `1.26.30` (1001), `1.26.40` (2168), `1.26.45` (2170), `1.26.50` (2193) | `1.26.50` on Java 21 |

Bedrock players connect to Lumi through [CloudburstMC Protocol](#bedrock-players-through-cloudburstmc-protocol);
see the [Lumi guide](../servers/lumi/index.md).

## Support levels and policies

| Level | Player: protocol library release | Process: platform version |
|---|---|---|
| `VERIFIED` | The release verifies the version: Anvil's live matrix runs it | The live matrix runs it |
| `COMPATIBLE` | A release shipped with Anvil lists the version | The provider's data knows the version |
| `UNTESTED` | Only [additional release data](#add-release-data) lists the version | Newer than every known version, or unknown |
| `UNSUPPORTED` | No release lists the version | Older than the platform's first Java row |

A process is assessed as the weakest of its platform version and its Java version; the
[Java guide](../../provisioning/java/index.md#support-levels) describes the Java side. A player is
assessed by its release alone.

The support policy decides what runs:

| Level | `LENIENT` (default) | `STRICT` |
|---|---|---|
| `VERIFIED` | Runs | Runs |
| `COMPATIBLE` | Runs and prints an `[Anvil] Info:` line | Runs and prints an `[Anvil] Info:` line |
| `UNTESTED` | Runs and prints an `[Anvil] Warning:` line | Refused |
| `UNSUPPORTED` | Refused | Refused |

A process that runs above its Java maximum through a bypass property, such as Paper `1.16.5` on Java 17,
is `UNTESTED` unless the live matrix verifies that combination.

`LENIENT` lets a newer Minecraft release keep working without an Anvil update when nothing it relies
on changed. Use `STRICT` when only combinations Anvil knows may run, for example in release
verification. A refused process fails the scenario before anything is downloaded or launched; a
refused player fails its creation. Set the engine default with `EngineOptions.supportPolicy(...)`,
`anvil { engine { supportPolicy("strict") } }`, or `-Danvil.supportPolicy=strict`. A scenario
overrides it:

```java
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.SupportPolicy;

AnvilScenario strict = scenario.toBuilder()
		.supportPolicy(SupportPolicy.STRICT)
		.build();
```

This fragment assumes an existing `AnvilScenario scenario`, such as the one your definition returns.

## Select a protocol library

Each player selects a library and its exact release when it is created. A player created from an
[account lease](../../../players/authentication/index.md) always uses the library that stores the leased
account, whatever the scenario or engine declares. Every other player selects:

1. `PlayerOptions.protocolLibrary(...)`, else `AnvilScenario.protocolLibrary(...)`, else the engine's
   `protocolLibrary`, set through `EngineOptions`, `anvil { engine { protocolLibrary("mcprotocol") } }`,
   or `-Danvil.protocolLibrary=mcprotocol`.
2. Without a declaration, every installed library whose releases list the player's version is ranked
   by support level, and the strongest one wins. A tie at the strongest level is refused with the
   tied library IDs; set `protocolLibrary` to choose one.

Each bundled library serves the servers of its own edition, so with MCProtocolLib and CloudburstMC
Protocol installed, every player uses the library of its servers' edition without a declaration.

## Add release data

Release data supplied by you adds releases to a library, for example a Minecraft version published
after your Anvil version. Point the engine at a file for one library ID:

```kotlin
anvil {
	engine {
		protocolReleases("mcprotocol", file("anvil/mcprotocol-releases.toml"))
	}
}
```

Embedding code uses `EngineOptions.builder().protocolRelease("mcprotocol", path)`, and a JVM property
uses `-Danvil.protocolReleases.mcprotocol=<file>`. The file uses the library's own format; for
MCProtocolLib, that is the schema of the bundled `mcprotocol-releases.toml`. This template shows one
release; replace every value in angle brackets:

```toml
[[release]]
version = "<MCProtocolLib release>"
edition = "java"
module = "<group>:<name>:<version>"
protocol = <protocol number>
minecraft = ["<Minecraft version>"]
verified = []
java = <minimum Java feature version>
features = ["ONLINE_AUTHENTICATION"]

[[release.artifact]]
module = "<group>:<name>:<version>"
url = "<URL of the JAR>"
sha256 = "<SHA-256 of the JAR>"
```

`edition` names the edition of the servers the release reaches. When the listed versions speak
different protocol numbers, replace `protocol` with a `[release.protocol]` table that maps each listed
version to its number, placed before the artifact tables:

```toml
[release.protocol]
"<Minecraft version>" = <protocol number>
```

List the release module and every JAR of its runtime in `[[release.artifact]]` tables, in class path
order. Your releases are assessed as `UNTESTED`, so `STRICT` refuses them. The engine reads the file
when it starts and refuses invalid data, an unknown library ID, a Minecraft version that another
release of the same edition already lists, and a release key that another release already has. A release with an empty `sha256` is listed, but players that select it are
refused until you set the checksum. A built-in release cannot be changed: a release with a built-in
release's version is ignored when its content is identical and refused otherwise.

The worker runs your release with the segments of the newest release key at or below yours, and each
segment's linkage is checked against your release first. When a segment does not link, its capability
is unavailable with the first missing class or member, and a client segment that does not link fails
the player. A release older than `1.18.2` has no segments and cannot start.

## Native client selection

Without a `PlayerOptions.clientVersion` override, Anvil derives the native version from the player's
connection target and its reachable servers. Their platforms decide the edition: only a release of
the same edition serves them, so a release of another edition that lists the same version number is
never selected. An explicit override must still be listed by a release of that edition in the
selected protocol library and match the reachable servers.

For an executable supplied through `Distribution.local(...)` or `Distribution.artifact(...)`, set
`.minecraftVersion("1.21.11")` on the server declaration, changing the value to match that JAR.
Anvil cannot determine the required native client solely from an arbitrary artifact filename.

A mixed topology with reachable servers of different editions or versions fails validation. Anvil does not insert
a protocol translator or silently fall back to ViaVersion. If you test several versions, define separate
scenarios or separate [scenario definitions](../../index.md).

## Live matrix

Anvil's live matrix runs these Minecraft versions, one for each MCProtocolLib release key. Every
version runs directly on Paper, and every version NeoForge supports runs directly on NeoForge. The
current versions also run directly on Spigot and behind Velocity and BungeeCord, routed to Paper and Spigot.

| Minecraft version | Paper build | Spigot selection | NeoForge release |
|---|---|---|---|
| `1.18.2` | `388` | Not in the live matrix | Not supported |
| `1.21.1` | `133` | Not in the live matrix | `21.1.256` |
| `1.21.11` | `132` | [Pinned GetBukkit JAR](../servers/spigot/index.md#recorded-content-pins) | `21.11.45` |
| `26.1.2` | `74` | [Pinned GetBukkit JAR](../servers/spigot/index.md#recorded-content-pins) | `26.1.2.114` |

The live matrix also runs Lumi `1.6.9` on Bedrock `1.26.50` directly, through its console and agent.

The proxy selections in that matrix are Velocity `3.5.1` build `615` and BungeeCord Jenkins build
`2085`. These are the repository's pinned compatibility selections, not moving recommendations to use
an upstream latest build.

## Check an upgrade

Update the server pin, native client override if any, and any explicit Java requirement together;
without a requirement, the new version's preferred LTS is selected automatically. Run the smallest
journey that exercises the route used by your plugin before expanding the test suite. A successful
server boot alone does not verify player protocol compatibility or proxy forwarding.

See [Java selection](../../provisioning/java/index.md) for supplying another JDK, and
[player routing diagnostics](../../../../help/troubleshooting/players/index.md) for mismatches.
