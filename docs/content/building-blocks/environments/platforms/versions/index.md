---
title: Versions and compatibility
description: Find the Minecraft versions each protocol library and platform supports, how Anvil assesses them, and how to run a version Anvil does not ship.
---

Choose one native Minecraft version for every server reachable by a player. A proxy has its own
release version; that version does not select the simulated player's protocol.

Anvil assesses players and processes separately. A player speaks one Minecraft version through a
release of a protocol library. A server or proxy runs one platform version on one Java version.
Each assessment has a [support level](#support-levels-and-policies), and the support policy decides
which levels run.

## Players through MCProtocolLib

MCProtocolLib is the bundled protocol library, with the ID `mcprotocol`. Its releases are data:
`mcprotocol-releases.toml`, shipped inside `protocol-mcprotocol`. Each release speaks one wire
protocol and lists every Minecraft version that speaks it; its highest version is the release key.

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

## Servers and proxies

| Platform | Supported versions | Verified by the live matrix |
|---|---|---|
| Paper | `1.16.5` and newer | `1.18.2`, `1.21.1`, `1.21.11`, and `26.1.2` |
| Spigot | `1.16.5` and newer | `1.21.11` and `26.1.2` |
| NeoForge | `1.21.1` and newer | `1.21.1`, `1.21.11`, and `26.1.2` |
| Velocity | `3.3.0` and newer | `3.5.1` |
| BungeeCord | Every build, assessed by Java range only | None; the matrix runs build `2085` |

Each platform provider ships its known versions, its Java rows, its agent's minimum Java, and the
combinations Anvil's live matrix verifies as data. Planning picks one exact LTS release per process.
See [Java per platform version](../../provisioning/java/index.md#java-per-platform-version) for the
rows, the defaults, and the Java version of each verified combination.

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

With MCProtocolLib as the only installed library, every player uses it without a declaration.

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

List the release module and every JAR of its runtime in `[[release.artifact]]` tables, in class path
order. Your releases are assessed as `UNTESTED`, so `STRICT` refuses them. The engine reads the file
when it starts and refuses invalid data, an unknown library ID, and a Minecraft version that another
release already lists. A release with an empty `sha256` is listed, but players that select it are
refused until you set the checksum. A built-in release cannot be changed: a release with a built-in
release's version is ignored when its content is identical and refused otherwise.

The worker runs your release with the segments of the newest release key at or below yours, and each
segment's linkage is checked against your release first. When a segment does not link, its capability
is unavailable with the first missing class or member, and a client segment that does not link fails
the player. A release older than `1.18.2` has no segments and cannot start.

## Native client selection

Without a `PlayerOptions.clientVersion` override, Anvil derives the native version from the player's
connection target and its reachable servers. An explicit override must still be listed by a release
of the selected protocol library and match the reachable servers.

For an executable supplied through `Distribution.local(...)` or `Distribution.artifact(...)`, set
`.minecraftVersion("1.21.11")` on the server declaration, changing the value to match that JAR.
Anvil cannot determine the required native client solely from an arbitrary artifact filename.

A mixed topology with incompatible reachable server versions fails validation. Anvil does not insert
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
