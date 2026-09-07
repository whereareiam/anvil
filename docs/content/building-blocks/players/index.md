---
title: Overview
description: Create native clients, choose their connection targets, and drive a journey through capabilities.
---

Create simulated players from an active `ScenarioContext`. They belong to that execution and start
disconnected. Each name must be unique among the players currently registered in the context.

Use these clients in an automated journey or create them in an environment's setup hook when a
human-run scenario needs prepared participants. A real Minecraft client you join from is separate
from these code-controlled players.

1. [Create and configure a player](./connections/index.md). Offline players are the default.
2. If the journey needs an online account, configure [authentication](./authentication/index.md).
3. [Use capabilities](./capabilities/index.md) to connect, observe arrival, and perform actions.
4. [Release players](./connections/index.md#release-and-replace-a-player) when their work is complete,
   or let scenario teardown release them. Connection changes are covered by [Session](./capabilities/session/index.md).

Tests and embedding applications receive a `ScenarioContext` through
[JUnit](../../integrations/junit/selection/index.md) or an [embedded engine](../../integrations/embedding/index.md).
A [setup hook](../environments/definitions/index.md#prepare-live-state) instead receives `ScenarioAccess`,
which exposes the same player and process services without owning context closure. Choose the
surrounding workflow separately from the player behavior.

A player is a network client, so asynchronous effects need explicit waits. A connected session proves
login completion; use the `Server` capability to await an agent-observed route to a named backend.
That route can come from the proxy's connected-server observation. Use a backend-specific agent
operation when you need proof from that backend itself, and assert application state separately.
