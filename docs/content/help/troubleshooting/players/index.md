---
title: Players and routing
description: Diagnose connections, capability discovery, version matching, and backend observations.
---

Separate creation, connection, and observation. Creating a player registers its name in the
scenario; it does not log in. The Session capability starts the connection, and a bounded wait
establishes whether the expected state was observed.

## Check the connection target

The player's `connectTo` selects a named server or proxy. When omitted, the scenario entrypoint is
used. Check the names against the scenario declaration rather than a stale handle from another run.
A native client must match every server reachable through that target. Anvil does not add
ViaVersion or translate the client's protocol when a topology has mixed incompatible versions.

Read the entry process's console first, then the target backend's console. Confirm that the proxy
lists the intended backend and has a valid default server. See
[forwarding](../../../building-blocks/environments/platforms/proxies/forwarding/index.md).

## Check the protocol library

Creating a player selects a protocol library and the exact release for its Minecraft version, and
applies the support policy before the player exists:

| Message | Check |
|---|---|
| `No installed protocol library supports Minecraft <version>` | Install a library that supports the version, or [add release data](../../../building-blocks/environments/platforms/versions/index.md#add-release-data) for a newer version |
| `Protocol libraries [...] support Minecraft <version> equally` | Set `protocolLibrary` on the player, the scenario, or the engine |
| `Protocol library '<id>' selected for player '<name>' does not support Minecraft <version>` | Choose another library, or a server version the selected library lists |
| `The STRICT support policy refuses ...` | The release is `UNTESTED`; use the default `LENIENT` policy or a version Anvil's data lists |
| `MCProtocolLib release <version> has no pinned checksum for <module>` | Run the named pin task, or set the `sha256` in your own release data |
| `Authenticated account '<id>' is not stored by protocol library '<library>'` | Sign the account in through that library, or select the library that stores it |

The [versions page](../../../building-blocks/environments/platforms/versions/index.md) lists the
releases and support levels.

## Capability discovery

| Symptom | Check |
|---|---|
| A capability is absent | Its public API alone is insufficient; install its consumer wiring artifact |
| A declared dependency is unavailable | Install compatible wiring for the predecessor capabilities |
| A capability reports `not installed by the <library> worker` | The library's worker has no wiring for it: install the capability's bundle, which brings its library sides, or select a library that supports it |
| A capability reports a missing class or member | The selected segment does not link against the player's release; the reason names the first missing member |
| The same capability has competing providers | Keep one selected implementation for that type and protocol library |
| An external adapter is ignored | Check its `supportedLibraries()` and service descriptor |
| An agent operation is unknown | Install the extension JAR under `plugins/anvil-agent-extensions` and verify its operation namespace |

Library selection happens before capability composition. MCProtocol-specific adapters do not
become compatible with another protocol library by putting them on its classpath. See
[capability installation](../../../building-blocks/players/capabilities/index.md) and
[extension packaging](../../../extending/packaging/index.md).

## Verify the observation you need

A client connection proves a protocol session was established. To verify the backend, use the
Server capability's observed route and identity. Its joined wait can be satisfied by a proxy agent's
connected-backend report; use a backend-specific operation if you need independent confirmation
inside that server. A successful reconnect by itself does not prove
that your plugin restored data; assert the plugin-specific behavior after reconnecting.

Use bounded capability waits for messages, connection changes, and backend arrival. A fixed sleep
only delays the test. If a wait times out, inspect the reported recent history and confirm that the
action actually triggers the expected event. When old messages could satisfy an assertion, use the
distinct message text for each action, and inspect the captured history as described in [Messages](../../../building-blocks/players/capabilities/messages/index.md).

For online players, verify that the selected account exists in the configured private account root used by
the run. Add or import the account through Anvil's account manager; do not put tokens in scenario
code or Gradle properties. See [Authentication](../../../building-blocks/players/authentication/index.md).
