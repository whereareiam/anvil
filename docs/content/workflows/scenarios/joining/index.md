---
title: Joining an environment
description: Connect your Minecraft Java Edition client to a running local or explicitly shared environment.
---

Start a [prepared scenario](../running/index.md) and keep its terminal open. You join with your own
Minecraft Java Edition client; Anvil does not launch or control that rendered client for you.

## Join from this machine

1. Wait for the runner to report that the scenario is ready.
2. Read the endpoint printed after `Join:`. Enter `status` in the runner to print it again.
3. Open Multiplayer in your Minecraft client and use Direct Connection with that host and port.

For the `local-paper` catalog example, use a Minecraft `1.21.11` client. A loopback address is usually
`127.0.0.1` and the port is allocated for the run. Copy the address and port, omitting any leading
slash shown by Java's address formatting. Do not assume the default Minecraft port.

When the entrypoint is a proxy, connect to that proxy endpoint. Connecting to a listed backend
directly bypasses the route that the scenario was prepared to exercise. Check
[versions and compatibility](../../../building-blocks/environments/platforms/versions/index.md) for
other native server versions; the proxy's own release number is not your client version.

## Choose the intended identity

The sample server uses offline authentication. If your environment needs authenticated connections,
set `.onlineMode(true)` on the direct server or entry proxy, and sign into your Minecraft client
normally. Let Anvil configure [forwarded backends](../../../building-blocks/environments/platforms/proxies/forwarding/index.md)
through the declared topology.

`anvilLogin` configures a protocol provider's stored profile for simulated players. It is not a login
step for the Minecraft client you operate yourself. If a setup hook also creates simulated players,
give them distinct names so they do not compete with your client for the same player identity.

## Join from another machine

Game listeners default to loopback. To expose a local prepared environment, add all three settings
to its `AnvilScenario` builder in the catalog:

```java
.manual(true)
.allowLanBinding(true)
.bindAddress("0.0.0.0")
```

This is a builder fragment for the [catalog example](../catalogs/index.md). Keep its processes,
entrypoint, and other settings, then relaunch the scenario. On the other machine, join using the
Anvil host's reachable LAN address and the allocated game port. `0.0.0.0` is a bind address, not the
destination to enter in the client.

The listener must also be reachable through the host's network and firewall. Local agent endpoints
stay on loopback with per-run authentication. Docker publishes agent endpoints on host loopback;
its internal container binding and supported local-daemon setup are described under
[execution providers](../../../building-blocks/environments/configuration/execution/index.md).

Manual mode does not accept the EULA automatically, bypass authentication, or translate Minecraft
versions. Keep those settings explicit when sharing an environment.

## Rejoin after replacement

If the runner switches or restarts the whole environment, read its newly printed entrypoint and
reconnect. Leaving your client open does not preserve its server session. A successful reconnect
also does not prove that your plugin's saved state survived; inspect that behavior in the application.
