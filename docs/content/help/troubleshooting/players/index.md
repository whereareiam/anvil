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

## Capability discovery

| Symptom | Check |
|---|---|
| A capability is absent | Its public API alone is insufficient; install its consumer wiring artifact |
| A declared dependency is unavailable | Install compatible wiring for the predecessor capabilities |
| The same capability has competing providers | Keep one selected implementation for that type and protocol |
| An external adapter is ignored | Check its declared protocol IDs and service descriptor |
| An agent channelOperation is unknown | Install the extension JAR under `plugins/anvil-agent-extensions` and verify its channelOperation namespace |

Provider selection happens before capability composition. MCProtocol-specific adapters do not
become compatible with another backend by putting them on its classpath. See
[capability installation](../../../building-blocks/players/capabilities/index.md) and
[extension packaging](../../../extending/packaging/index.md).

## Verify the observation you need

A client connection proves a protocol session was established. To verify the backend, use the
Server capability's observed route and identity. Its joined wait can be satisfied by a proxy agent's
connected-backend report; use a backend-specific channelOperation if you need independent confirmation
inside that server. A successful reconnect by itself does not prove
that your plugin restored data; assert the plugin-specific behavior after reconnecting.

Use bounded capability waits for messages, connection changes, and backend arrival. A fixed sleep
only delays the test. If a wait times out, inspect the reported recent history and confirm that the
action actually triggers the expected channelEvent. When old messages could satisfy an assertion, use the
distinct message text for each action, and inspect the captured history as described in [Messages](../../../building-blocks/players/capabilities/messages/index.md).

For online players, verify that the selected profile exists in the same private cache root used by
the run. Authenticate explicitly with `anvilLogin --auth-profile=<name>`; do not put tokens in
scenario code or Gradle properties. See [Authentication](../../../building-blocks/players/authentication/index.md).
