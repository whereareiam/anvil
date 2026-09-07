---
title: Versions and compatibility
description: Match server versions, native protocol players, proxy routes, and required Java runtimes.
---

Choose one native Minecraft version for every server reachable by a player. A proxy has its own
release version; that version does not select the simulated player's protocol.

## Bundled native versions

The current MCProtocol catalog contains these Minecraft versions. The compatibility test catalog
covers direct Paper and Spigot sessions and Velocity/BungeeCord routes to each server platform.

| Minecraft version | Paper build | Spigot selection | Server Java minimum |
|---|---|---|---|
| `1.21.11` | `132` | [Pinned GetBukkit JAR](../servers/spigot/index.md#recorded-content-pins) | 21 |
| `26.1.2` | `74` | [Pinned GetBukkit JAR](../servers/spigot/index.md#recorded-content-pins) | 25 |

The proxy selections in that catalog are Velocity `3.5.1` build `615` and BungeeCord Jenkins build
`2085`. Both bundled proxy providers require Java 21. These are the repository's pinned compatibility
selections, not moving recommendations to use an upstream latest build.

## Native client selection

Without a `PlayerOptions.clientVersion` override, Anvil derives the native version from the player's
connection target and its reachable servers. An explicit override must still be supported by the
installed protocol provider and match the reachable servers.

For an executable supplied through `Distribution.local(...)` or `Distribution.artifact(...)`, set
`.minecraftVersion("1.21.11")` on the server declaration, changing the value to match that JAR.
Anvil cannot determine the required native client solely from an arbitrary artifact filename.

A mixed topology with incompatible reachable server versions fails validation. Anvil does not insert
a protocol translator or silently fall back to ViaVersion. If you test several versions, define separate
scenarios or a [scenario catalog](../../index.md).

## Check an upgrade

Update the server pin, native client override if any, and Java requirements together. Run the smallest
journey that exercises the route used by your plugin before expanding the test suite. A successful
server boot alone does not verify player protocol compatibility or proxy forwarding.

See [Java selection](../../provisioning/java/index.md) for supplying another JDK, and
[player routing diagnostics](../../../../help/troubleshooting/players/index.md) for mismatches.
